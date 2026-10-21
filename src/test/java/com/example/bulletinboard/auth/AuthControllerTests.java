package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.ad.AdRepository;
import com.example.bulletinboard.support.TestAccounts;
import com.example.bulletinboard.support.TestAccounts.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AdRepository adRepository;

    @LocalServerPort
    private int port;

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void signUpCreatesAccountLogsInAndRotatesTheToken() throws Exception {
        Cookie xsrf = TestAccounts.fetchXsrfCookie(mockMvc);
        String email = TestAccounts.uniqueEmail();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(xsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("  " + email.toUpperCase() + " ", " Ann ", TestAccounts.markerPassword())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Ann"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getCookie(TestAccounts.XSRF_COOKIE).getValue()).isNotEqualTo(xsrf.getValue());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Ann"));
    }

    @Test
    void signUpSetsAnHttpOnlySameSiteSessionCookieWithoutExpiry() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> me = client.send(HttpRequest.newBuilder(url("/api/v1/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String xsrf = cookieValue(me.headers().allValues("Set-Cookie"), TestAccounts.XSRF_COOKIE);

        HttpResponse<String> signup = client.send(HttpRequest.newBuilder(url("/api/v1/auth/signup"))
                .header("Content-Type", "application/json")
                .header("Cookie", TestAccounts.XSRF_COOKIE + "=" + xsrf)
                .header(TestAccounts.XSRF_HEADER, xsrf)
                .POST(HttpRequest.BodyPublishers.ofString(signupJson(TestAccounts.uniqueEmail(), "Ann",
                        TestAccounts.markerPassword())))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(signup.statusCode()).isEqualTo(201);
        String sessionCookie = signup.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(AuthController.SESSION_COOKIE + "="))
                .findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax").contains("Path=/")
                .doesNotContainIgnoringCase("Max-Age").doesNotContainIgnoringCase("Expires");
    }

    @Test
    void signUpWithoutAntiForgeryTokenIsRefusedWith403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(TestAccounts.uniqueEmail(), "Ann", TestAccounts.markerPassword())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("csrf_invalid"))
                .andExpect(jsonPath("$.detail").value("Invalid or missing anti-forgery token"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void duplicateEmailIsRefusedWith409() throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(" " + ann.email().toUpperCase(), "Other", TestAccounts.markerPassword())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"))
                .andExpect(jsonPath("$.detail").value("Email already registered"));

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidFieldsAreRefusedWith400AndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("not-an-email", "   ", "short7!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "displayName", "password")));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void logInReturnsTheUserAndChangesTheSessionId() throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");
        MockHttpSession preLoginSession = new MockHttpSession();
        String preLoginId = preLoginSession.getId();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .session(preLoginSession)
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ann.email(), ann.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.id()))
                .andExpect(jsonPath("$.displayName").value("Ann"))
                .andExpect(cookie().exists(TestAccounts.XSRF_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(preLoginId);
        mockMvc.perform(get("/api/v1/auth/me").session(preLoginSession))
                .andExpect(jsonPath("$.user.id").value(ann.id()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ann.email(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody@example.com", ann.password())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"))
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void fiveFailuresThenTheCorrectPasswordGive423() throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(ann.email(), "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ann.email(), ann.password())))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("account_locked"))
                .andExpect(jsonPath("$.detail").value("Account temporarily locked. Try again later."));
    }

    @Test
    void meForAVisitorReturnsNullUserAndIssuesTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(nullValue()))
                .andExpect(cookie().exists(TestAccounts.XSRF_COOKIE))
                .andExpect(cookie().httpOnly(TestAccounts.XSRF_COOKIE, false));
    }

    @Test
    void logOutEndsTheSessionAndTheOldSessionIsAnonymous() throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");

        mockMvc.perform(post("/api/v1/auth/logout").with(ann.loggedIn()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthController.SESSION_COOKIE, 0));

        assertThat(ann.session().isInvalid()).isTrue();
        MockHttpSession replayed = new MockHttpSession(null, ann.session().getId());
        mockMvc.perform(get("/api/v1/auth/me").session(replayed))
                .andExpect(jsonPath("$.user").value(nullValue()));
        mockMvc.perform(post("/api/v1/ads")
                        .session(replayed)
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike", "description": "Almost new"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));
        assertThat(adRepository.count()).isZero();
    }

    @Test
    void logOutWhileLoggedOutStillGives204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc))))
                .andExpect(status().isNoContent());
    }

    @Test
    void passwordsHashesAndEmailsNeverReachResponsesOrLogs(CapturedOutput output) throws Exception {
        String password = TestAccounts.markerPassword();
        String email = TestAccounts.uniqueEmail();
        StringBuilder bodies = new StringBuilder();

        User ann = TestAccounts.signUp(mockMvc, email, "Ann", password);
        bodies.append(mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password + "-wrong")))
                .andReturn().getResponse().getContentAsString());
        bodies.append(mockMvc.perform(post("/api/v1/auth/login")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andReturn().getResponse().getContentAsString());
        bodies.append(mockMvc.perform(get("/api/v1/auth/me").session(ann.session()))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/auth/logout").with(ann.loggedIn())).andExpect(status().isNoContent());

        assertThat(bodies.toString()).doesNotContain(password).doesNotContain("$2a$");
        assertThat(output.getAll())
                .contains("event=signup accountId=" + ann.id() + " outcome=ok path=/api/v1/auth/signup")
                .contains("event=login_failure accountId=" + ann.id() + " outcome=bad_credentials")
                .contains("event=login_success accountId=" + ann.id())
                .contains("event=logout accountId=" + ann.id())
                .doesNotContain(password)
                .doesNotContain(email)
                .doesNotContain(ann.xsrfCookie().getValue());
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(name + "="))
                .map(header -> header.substring(name.length() + 1, header.indexOf(';')))
                .findFirst().orElseThrow();
    }

    private static String signupJson(String email, String displayName, String password) {
        return """
                {"email": "%s", "displayName": "%s", "password": "%s"}
                """.formatted(email, displayName, password);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
