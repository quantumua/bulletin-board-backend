package com.example.bulletinboard.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    @Test
    void registersAndSignsIn() throws Exception {
        register("alice@example.com", "correct-horse")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.id").value(repository.findByEmail("alice@example.com").orElseThrow().getId()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(authenticated().withUsername("alice@example.com"));
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        register("not-an-email", "correct-horse")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsTooShortPassword() throws Exception {
        register("alice@example.com", "a".repeat(7))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsTooLongPassword() throws Exception {
        register("alice@example.com", "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsPasswordOverBcryptByteLimit() throws Exception {
        // 42 characters but 84 UTF-8 bytes
        register("alice@example.com", "пароль".repeat(7))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(repository.count()).isZero();
    }

    @Test
    void acceptsMultibytePasswordUpToByteLimit() throws Exception {
        // 36 characters, exactly 72 UTF-8 bytes
        register("alice@example.com", "пароль".repeat(6)).andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register("alice@example.com", "correct-horse").andExpect(status().isCreated());

        register(" Alice@Example.COM ", "another-pass")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
        assertThat(repository.count()).isEqualTo(1);
    }

    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private MockHttpSession signedInSession() throws Exception {
        return (MockHttpSession) register("alice@example.com", "correct-horse")
                .andExpect(status().isCreated())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void signsInWithCaseInsensitiveEmail() throws Exception {
        register("alice@example.com", "correct-horse");
        long id = repository.findByEmail("alice@example.com").orElseThrow().getId();

        login("ALICE@example.com", "correct-horse", new MockHttpSession())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(authenticated().withUsername("alice@example.com"));
    }

    @Test
    void rotatesSessionIdOnSignIn() throws Exception {
        register("alice@example.com", "correct-horse");
        var session = new MockHttpSession();
        String idBefore = session.getId();

        String idAfter = login("alice@example.com", "correct-horse", session)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false).getId();

        assertThat(idAfter).isNotEqualTo(idBefore);
    }

    @Test
    void rejectsWrongPasswordAndUnknownEmailAlike() throws Exception {
        register("alice@example.com", "correct-horse");

        login("alice@example.com", "wrong-password", new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andExpect(unauthenticated());
        login("nobody@example.com", "correct-horse", new MockHttpSession())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andExpect(unauthenticated());
    }

    @Test
    void rejectsBlankSignInFields() throws Exception {
        login(" ", "correct-horse", new MockHttpSession())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        login("alice@example.com", "", new MockHttpSession())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsSignInWithoutCsrfToken() throws Exception {
        register("alice@example.com", "correct-horse");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alice@example.com", "password": "correct-horse"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsCurrentUserWhenSignedIn() throws Exception {
        MockHttpSession session = signedInSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.id").value(repository.findByEmail("alice@example.com").orElseThrow().getId()))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void rejectsCurrentUserWhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void signsOut() throws Exception {
        MockHttpSession session = signedInSession();

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signsOutWhenAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
