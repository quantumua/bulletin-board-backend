package com.example.bulletinboard.user;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real SPA CSRF flow. Kept apart from other tests and given a fresh context because
 * {@code SecurityMockMvcRequestPostProcessors.csrf()} permanently replaces the filter's cookie token repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void registersWithTokenFromCookieIssuedByCurrentUserRequest() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/register")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alice@example.com", "password": "correct-horse"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(authenticated().withUsername("alice@example.com"));
    }

    @Test
    void rotatesTokenOnSignInAndIssuesTheNewOneInTheSameResponse() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/auth/me"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alice@example.com", "password": "correct-horse"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        // The response first clears the old cookie, then sets the new one; browsers keep the last
        Cookie rotated = Arrays.stream(registration.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(xsrf.getValue());

        mockMvc.perform(post("/api/auth/logout")
                        .session((MockHttpSession) registration.getRequest().getSession(false))
                        .cookie(rotated)
                        .header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isNoContent());
    }
}
