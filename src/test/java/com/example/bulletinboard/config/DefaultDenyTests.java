package com.example.bulletinboard.config;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.ad.Ad;
import com.example.bulletinboard.ad.AdRepository;
import com.example.bulletinboard.support.TestAccounts;
import com.example.bulletinboard.support.TestAccounts.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default deny (NFR5.1; Build and Test addition from the code review): every request that is not
 * explicitly permitted is refused with a problem-details answer carrying a {@code code}, for visitors
 * (401 {@code not_authenticated}) and logged-in users alike (403 {@code forbidden}). Nothing is changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DefaultDenyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private UserAccountRepository accountRepository;

    private User ann;
    private Ad annsAd;

    @BeforeEach
    void setUp() throws Exception {
        adRepository.deleteAll();
        accountRepository.deleteAll();
        ann = TestAccounts.signUp(mockMvc, "Ann");
        annsAd = adRepository.save(new Ad("Bike", "Almost new", ann.id()));
    }

    @Test
    void deletingAnAdIsRefusedForVisitorsAndEvenForItsOwner() throws Exception {
        expectProblem(mockMvc.perform(delete("/api/v1/ads/{id}", annsAd.getId())
                .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))), 401, "not_authenticated");
        expectProblem(mockMvc.perform(delete("/api/v1/ads/{id}", annsAd.getId())
                .with(ann.loggedIn())), 403, "forbidden");
        expectProblem(mockMvc.perform(delete("/api/ads/{id}", annsAd.getId())
                .with(ann.loggedIn())), 403, "forbidden");

        assertThat(adRepository.findById(annsAd.getId())).isPresent();
    }

    @Test
    void deleteWithoutAntiForgeryTokenIsRefusedByTheTokenCheckFirst() throws Exception {
        expectProblem(mockMvc.perform(delete("/api/v1/ads/{id}", annsAd.getId())
                .session(ann.session())), 403, "csrf_invalid");

        assertThat(adRepository.findById(annsAd.getId())).isPresent();
    }

    @Test
    void patchOnAnAdIsRefusedEvenForItsOwner() throws Exception {
        expectProblem(mockMvc.perform(patch("/api/v1/ads/{id}", annsAd.getId())
                .with(ann.loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Changed\"}")), 403, "forbidden");

        assertThat(adRepository.findById(annsAd.getId()).orElseThrow().getTitle()).isEqualTo("Bike");
    }

    @Test
    void unmappedWritesUnderApiAreRefused() throws Exception {
        expectProblem(mockMvc.perform(post("/api/v1/admin/users")
                .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")), 401, "not_authenticated");
        expectProblem(mockMvc.perform(post("/api/v1/admin/users")
                .with(ann.loggedIn())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")), 403, "forbidden");
        expectProblem(mockMvc.perform(post("/api/v1/auth/me")
                .with(ann.loggedIn())), 403, "forbidden");
    }

    @Test
    void unmappedReadsUnderApiOutsideTheOpenPathsAreRefused() throws Exception {
        expectProblem(mockMvc.perform(get("/api/v1/users")), 401, "not_authenticated");
        expectProblem(mockMvc.perform(get("/api/v1/users").with(ann.loggedIn())), 403, "forbidden");
    }

    @Test
    void pathsOutsideApiAreRefusedWithACode() throws Exception {
        expectProblem(mockMvc.perform(get("/actuator/env")), 401, "not_authenticated");
        expectProblem(mockMvc.perform(get("/index.html")), 401, "not_authenticated");
        expectProblem(mockMvc.perform(get("/index.html").with(ann.loggedIn())), 403, "forbidden");
    }

    @Test
    void explicitlyOpenReadsStayOpen() throws Exception {
        mockMvc.perform(get("/api/v1/ads")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ads/{id}", annsAd.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/ads")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isOk());
    }

    private static void expectProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }
}
