package com.example.bulletinboard.user;

import com.example.bulletinboard.ad.AdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserDirectoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listedUserHasExactlyIdUsernameAndCreatedAt() throws Exception {
        userRepository.save(new AppUser("Alice", "secret-hash"));

        mockMvc.perform(get("/api/users").with(user("Alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].username").value("Alice"))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].usernameLower").doesNotExist());
    }

    @Test
    void listsUsersNewestFirst() throws Exception {
        userRepository.save(new AppUser("first", "hash"));
        pause();
        userRepository.save(new AppUser("second", "hash"));

        mockMvc.perform(get("/api/users").with(user("first")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("second"))
                .andExpect(jsonPath("$[1].username").value("first"));
    }

    @Test
    void usersRegisteredInTheSameInstantAreOrderedByIdDescending() throws Exception {
        userRepository.save(new AppUser("first", "hash"));
        userRepository.save(new AppUser("second", "hash"));
        userRepository.save(new AppUser("third", "hash"));
        // Force identical timestamps: Instant.now() alone cannot guarantee a tie
        jdbcTemplate.update("update users set created_at = ?", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(jdbcTemplate.queryForObject("select count(distinct created_at) from users", Integer.class))
                .isEqualTo(1);

        mockMvc.perform(get("/api/users").with(user("first")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("third"))
                .andExpect(jsonPath("$[1].username").value("second"))
                .andExpect(jsonPath("$[2].username").value("first"));
    }

    @Test
    void soleCallerGetsASingleElementArray() throws Exception {
        userRepository.save(new AppUser("alice", "hash"));

        mockMvc.perform(get("/api/users").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void anonymousGetsUnauthorizedProblemDetailAndNoUserData() throws Exception {
        userRepository.save(new AppUser("alice", "hash"));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alice"))));
    }

    // --- write methods

    @Test
    void writeMethodsAreNotOfferedToLoggedInClients() throws Exception {
        userRepository.save(new AppUser("alice", "hash"));

        for (var method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            mockMvc.perform(request(method, "/api/users").with(user("alice")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"mallory\"}"))
                    .andExpect(status().isMethodNotAllowed());
        }

        assertThat(userRepository.findAll()).extracting(AppUser::getUsername).containsExactly("alice");
    }

    @Test
    void writeMethodsWithoutCsrfTokenAreForbiddenBeforeTheMethodCheck() throws Exception {
        userRepository.save(new AppUser("alice", "hash"));

        // A real session makes the CSRF check apply; without the token it answers 403, not 405
        for (var method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            mockMvc.perform(request(method, "/api/users").with(user("alice")).session(new MockHttpSession()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void writeMethodsAreUnauthorizedWhenAnonymous() throws Exception {
        for (var method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            mockMvc.perform(request(method, "/api/users"))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(userRepository.count()).isZero();
    }

    // --- neighboring paths

    @Test
    void adsStayPublic() throws Exception {
        mockMvc.perform(get("/api/ads")).andExpect(status().isOk());
    }

    @Test
    void h2ConsoleStillAnswersUnauthorized() throws Exception {
        mockMvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }

    // --- end to end

    @Test
    void newlyRegisteredUserIsListedWithItsRegistrationTime() throws Exception {
        String credentials = "{\"username\": \"alice\", \"password\": \"s3cret-pass\"}";
        // Truncated because the database may store fewer fractional digits than Instant.now() returns
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        String body = mockMvc.perform(get("/api/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andReturn().getResponse().getContentAsString();
        String createdAt = com.jayway.jsonpath.JsonPath.read(body, "$[0].createdAt");

        assertThat(Instant.parse(createdAt)).isAfterOrEqualTo(before);
    }

    // Registrations a few milliseconds apart so createdAt alone decides the order
    private static void pause() throws InterruptedException {
        Thread.sleep(10);
    }
}
