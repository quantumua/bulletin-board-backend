package com.example.bulletinboard.auth;

import com.example.bulletinboard.ad.AdRepository;
import com.example.bulletinboard.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    private static final String CREDENTIALS = """
            {"username": "alice", "password": "s3cret-pass"}
            """;
    private static final String AD_JSON = """
            {"title": "Bike", "description": "Almost new"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdRepository adRepository;

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- registration

    @Test
    void registersUser() throws Exception {
        MvcResult result = mockMvc.perform(register(CREDENTIALS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCase() throws Exception {
        mockMvc.perform(register(CREDENTIALS)).andExpect(status().isCreated());

        mockMvc.perform(register("""
                        {"username": "ALICE", "password": "another-pass"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRegistration() throws Exception {
        String[] bodies = {
                """
                {"username": "al", "password": "s3cret-pass"}
                """,
                """
                {"username": "%s", "password": "s3cret-pass"}
                """.formatted("a".repeat(51)),
                """
                {"username": "alice", "password": "short"}
                """,
                """
                {"username": "alice", "password": "%s"}
                """.formatted("p".repeat(73)),
        };
        for (String body : bodies) {
            mockMvc.perform(register(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertThat(userRepository.count()).isZero();
    }

    // --- login

    @Test
    void logsInWithValidCredentials() throws Exception {
        mockMvc.perform(register(CREDENTIALS)).andExpect(status().isCreated());

        mockMvc.perform(login(CREDENTIALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()));
        // Cookie attributes (HttpOnly, SameSite=Lax) are asserted against a real container in SessionCookieTests
    }

    @Test
    void rejectsUnknownUserAndWrongPasswordIdentically() throws Exception {
        mockMvc.perform(register(CREDENTIALS)).andExpect(status().isCreated());

        MvcResult wrongPassword = mockMvc.perform(login("""
                        {"username": "alice", "password": "wrong-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        MvcResult unknownUser = mockMvc.perform(login("""
                        {"username": "nobody", "password": "wrong-password"}
                        """))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownUser.getResponse().getContentAsString());
        assertThat(wrongPassword.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(unknownUser.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void loginIgnoresCaseOfUsername() throws Exception {
        mockMvc.perform(register(CREDENTIALS)).andExpect(status().isCreated());

        mockMvc.perform(login("""
                        {"username": "Alice", "password": "s3cret-pass"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    // --- current user and logout

    @Test
    void meReturnsLoggedInUser() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void meRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void logoutEndsSessionAndExpiresCookie() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("JSESSIONID=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWhileAnonymousSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // --- CSRF

    @Test
    void sessionRequestWithoutCsrfTokenIsForbidden() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/api/ads").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        assertThat(adRepository.count()).isZero();
    }

    @Test
    void sessionRequestWithCsrfTokenIsProcessed() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/api/ads").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(AD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("alice"));
    }

    // --- helpers

    private MockHttpSession loggedInSession() throws Exception {
        mockMvc.perform(register(CREDENTIALS)).andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(login(CREDENTIALS)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(String body) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
