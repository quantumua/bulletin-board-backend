package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.ad.AdRepository;
import com.example.bulletinboard.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session-fixation and anti-forgery rotation checks through the real servlet container (NFR3.2, NFR3.3,
 * NFR3.5; Build and Test additions from the code review). Cookies are handled by hand, exactly as a
 * browser or an attacker replaying a captured cookie would send them, so a replayed session id really
 * is looked up in the container's session store.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionFixationTests {

    private static final String SESSION = AuthController.SESSION_COOKIE;
    private static final String XSRF = TestAccounts.XSRF_COOKIE;

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AdRepository adRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void logInWhileHoldingAnotherSessionReplacesItAndTheOldIdIsAnonymous() throws Exception {
        Browser annBrowser = new Browser();
        annBrowser.signUp(TestAccounts.uniqueEmail(), "Ann", TestAccounts.markerPassword());
        String annSession = annBrowser.session;

        Browser bobSetup = new Browser();
        String bobEmail = TestAccounts.uniqueEmail();
        String bobPassword = TestAccounts.markerPassword();
        bobSetup.signUp(bobEmail, "Bob", bobPassword);

        // Bob logs in from the browser that still holds Ann's session cookie
        HttpResponse<String> login = annBrowser.post("/api/v1/auth/login",
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(bobEmail, bobPassword));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(annBrowser.session).isNotNull().isNotEqualTo(annSession);
        assertThat(annBrowser.me()).contains("\"displayName\":\"Bob\"");

        // The pre-log-in id is gone: replaying it is anonymous for reads and writes
        Browser replay = Browser.withSession(this, annSession);
        assertThat(replay.me()).isEqualTo("{\"user\":null}");
        assertThat(replay.post("/api/v1/ads", "{\"title\": \"Bike\", \"description\": \"New\"}").statusCode())
                .isEqualTo(401);
        assertThat(adRepository.count()).isZero();
    }

    @Test
    void signUpWhileHoldingAnExistingSessionReplacesItAndTheOldIdIsAnonymous() throws Exception {
        Browser browser = new Browser();
        browser.signUp(TestAccounts.uniqueEmail(), "Ann", TestAccounts.markerPassword());
        String annSession = browser.session;

        // A second account is created from the same, logged-in browser
        HttpResponse<String> signup = browser.post("/api/v1/auth/signup",
                "{\"email\": \"%s\", \"displayName\": \"Cleo\", \"password\": \"%s\"}"
                        .formatted(TestAccounts.uniqueEmail(), TestAccounts.markerPassword()));
        assertThat(signup.statusCode()).isEqualTo(201);
        assertThat(browser.session).isNotNull().isNotEqualTo(annSession);
        assertThat(browser.me()).contains("\"displayName\":\"Cleo\"");

        Browser replay = Browser.withSession(this, annSession);
        assertThat(replay.me()).isEqualTo("{\"user\":null}");
        assertThat(replay.post("/api/v1/ads", "{\"title\": \"Bike\", \"description\": \"New\"}").statusCode())
                .isEqualTo(401);
    }

    @Test
    void anAttackerChosenSessionIdIsNeverAdopted() throws Exception {
        Browser bobSetup = new Browser();
        String email = TestAccounts.uniqueEmail();
        String password = TestAccounts.markerPassword();
        bobSetup.signUp(email, "Bob", password);

        String planted = "0123456789ABCDEF0123456789ABCDEF";
        Browser victim = Browser.withSession(this, planted);
        HttpResponse<String> login = victim.post("/api/v1/auth/login",
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(victim.session).isNotEqualTo(planted);

        Browser attacker = Browser.withSession(this, planted);
        assertThat(attacker.me()).isEqualTo("{\"user\":null}");
    }

    @Test
    void theAntiForgeryTokenChangesAfterSignUpLogInAndLogOut() throws Exception {
        Browser browser = new Browser();
        browser.fetchToken();
        String beforeSignup = browser.xsrf;
        String email = TestAccounts.uniqueEmail();
        String password = TestAccounts.markerPassword();

        browser.signUp(email, "Ann", password);
        String afterSignup = browser.xsrf;
        assertThat(afterSignup).isNotEqualTo(beforeSignup);

        HttpResponse<String> logout = browser.post("/api/v1/auth/logout", null);
        assertThat(logout.statusCode()).isEqualTo(204);
        String afterLogout = browser.xsrf;
        assertThat(afterLogout).isNotEqualTo(afterSignup);
        assertThat(cookieValue(logout, SESSION)).contains("");

        HttpResponse<String> login = browser.post("/api/v1/auth/login",
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(browser.xsrf).isNotEqualTo(afterLogout);

        // The rotated token works for the next write
        assertThat(browser.post("/api/v1/ads", "{\"title\": \"Bike\", \"description\": \"New\"}").statusCode())
                .isEqualTo(201);
    }

    @Test
    void theSessionCookieBeforeLogOutIsAnonymousAfterwards() throws Exception {
        Browser browser = new Browser();
        browser.signUp(TestAccounts.uniqueEmail(), "Ann", TestAccounts.markerPassword());
        String loggedInSession = browser.session;

        assertThat(browser.post("/api/v1/auth/logout", null).statusCode()).isEqualTo(204);

        Browser replay = Browser.withSession(this, loggedInSession);
        assertThat(replay.me()).isEqualTo("{\"user\":null}");
        HttpResponse<String> write = replay.post("/api/v1/ads", "{\"title\": \"Bike\", \"description\": \"New\"}");
        assertThat(write.statusCode()).isEqualTo(401);
        assertThat(write.body()).contains("\"code\":\"not_authenticated\"");
    }

    private static Optional<String> cookieValue(HttpResponse<?> response, String name) {
        List<String> values = new ArrayList<>();
        for (String header : response.headers().allValues("Set-Cookie")) {
            if (header.startsWith(name + "=")) {
                int end = header.indexOf(';');
                values.add(header.substring(name.length() + 1, end < 0 ? header.length() : end));
            }
        }
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getLast());
    }

    /**
     * A minimal cookie-keeping client for the two cookies the board uses.
     */
    private final class Browser {

        private String session;
        private String xsrf;

        static Browser withSession(SessionFixationTests outer, String sessionId) {
            Browser browser = outer.new Browser();
            browser.session = sessionId;
            return browser;
        }

        void fetchToken() throws IOException, InterruptedException {
            send(HttpRequest.newBuilder(url("/api/v1/auth/me")).GET());
        }

        String me() throws IOException, InterruptedException {
            return send(HttpRequest.newBuilder(url("/api/v1/auth/me")).GET()).body();
        }

        void signUp(String email, String displayName, String password) throws IOException, InterruptedException {
            HttpResponse<String> response = post("/api/v1/auth/signup",
                    "{\"email\": \"%s\", \"displayName\": \"%s\", \"password\": \"%s\"}"
                            .formatted(email, displayName, password));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        }

        HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
            if (xsrf == null) {
                fetchToken();
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(url(path))
                    .header(TestAccounts.XSRF_HEADER, xsrf)
                    .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) {
                builder.header("Content-Type", "application/json");
            }
            return send(builder);
        }

        private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            List<String> cookies = new ArrayList<>();
            if (session != null) {
                cookies.add(SESSION + "=" + session);
            }
            if (xsrf != null) {
                cookies.add(XSRF + "=" + xsrf);
            }
            if (!cookies.isEmpty()) {
                builder.header("Cookie", String.join("; ", cookies));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            cookieValue(response, SESSION).ifPresent(value -> session = value.isEmpty() ? null : value);
            cookieValue(response, XSRF).ifPresent(value -> xsrf = value.isEmpty() ? null : value);
            return response;
        }

        private URI url(String path) {
            return URI.create("http://localhost:" + port + path);
        }
    }
}
