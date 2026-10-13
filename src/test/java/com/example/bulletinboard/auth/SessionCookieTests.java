package com.example.bulletinboard.auth;

import com.example.bulletinboard.ad.AdRepository;
import com.example.bulletinboard.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real servlet container, because MockMvc does not emit the container's session cookie
 * and so cannot show its attributes or the end-to-end cookie and CSRF round trip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTests {

    private static final String CREDENTIALS = "{\"username\": \"alice\", \"password\": \"s3cret-pass\"}";
    private static final String AD_JSON = "{\"title\": \"Bike\", \"description\": \"Almost new\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdRepository adRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void sessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
        send("POST", "/api/auth/register", CREDENTIALS, null);

        HttpResponse<String> login = send("POST", "/api/auth/login", CREDENTIALS, null);

        assertThat(login.statusCode()).isEqualTo(200);
        String sessionCookie = setCookie(login, "JSESSIONID").orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void csrfCookieIsReadableAndRoundTrips() throws Exception {
        send("POST", "/api/auth/register", CREDENTIALS, null);
        HttpResponse<String> login = send("POST", "/api/auth/login", CREDENTIALS, null);
        String session = cookiePair(setCookie(login, "JSESSIONID").orElseThrow());

        HttpResponse<String> me = send("GET", "/api/auth/me", null, session);
        String csrfCookie = setCookie(me, "XSRF-TOKEN").orElseThrow();
        assertThat(csrfCookie).doesNotContain("HttpOnly");
        String token = cookiePair(csrfCookie).substring("XSRF-TOKEN=".length());

        HttpResponse<String> withoutToken = send("POST", "/api/ads", AD_JSON, session);
        assertThat(withoutToken.statusCode()).isEqualTo(403);

        HttpResponse<String> withToken = send("POST", "/api/ads", AD_JSON, session + "; XSRF-TOKEN=" + token,
                "X-XSRF-TOKEN", token);
        assertThat(withToken.statusCode()).isEqualTo(201);
        assertThat(withToken.body()).contains("\"author\":\"alice\"");
    }

    @Test
    void ordinaryReadsIssueTheCsrfCookieWhenTheClientHasNone() throws Exception {
        // Control for the user-directory test below: without it, "no Set-Cookie" could pass vacuously
        HttpResponse<String> ads = send("GET", "/api/ads", null, null);

        assertThat(setCookie(ads, "XSRF-TOKEN")).isPresent();
    }

    @Test
    void userDirectorySetsNoCookiesForLoggedInClient() throws Exception {
        send("POST", "/api/auth/register", CREDENTIALS, null);
        HttpResponse<String> login = send("POST", "/api/auth/login", CREDENTIALS, null);
        String session = cookiePair(setCookie(login, "JSESSIONID").orElseThrow());

        // The client holds a session but no XSRF-TOKEN cookie, so other endpoints would issue one
        HttpResponse<String> users = send("GET", "/api/users", null, session);

        assertThat(users.statusCode()).isEqualTo(200);
        assertThat(users.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void userDirectorySetsNoCookiesWhenAnonymous() throws Exception {
        HttpResponse<String> users = send("GET", "/api/users", null, null);

        assertThat(users.statusCode()).isEqualTo(401);
        assertThat(users.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void anonymousUnauthorizedResponseDoesNotStartASession() throws Exception {
        HttpResponse<String> me = send("GET", "/api/auth/me", null, null);

        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(setCookie(me, "JSESSIONID")).isEmpty();
    }

    @Test
    void anonymousWriteAfterAnAnonymousReadIsUnauthorizedNotForbidden() throws Exception {
        // Reproduces the old behavior: the 401 on /me started a session, so the next anonymous write hit the CSRF check
        HttpResponse<String> me = send("GET", "/api/auth/me", null, null);
        String cookies = me.headers().allValues("Set-Cookie").stream().map(SessionCookieTests::cookiePair)
                .reduce((a, b) -> a + "; " + b).orElse(null);

        HttpResponse<String> create = send("POST", "/api/ads", AD_JSON, cookies);

        assertThat(create.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, String body, String cookies, String... headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<String> setCookie(HttpResponse<?> response, String name) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        return cookies.stream().filter(cookie -> cookie.startsWith(name + "=")).findFirst();
    }

    private static String cookiePair(String setCookie) {
        return setCookie.substring(0, setCookie.indexOf(';') < 0 ? setCookie.length() : setCookie.indexOf(';'));
    }
}
