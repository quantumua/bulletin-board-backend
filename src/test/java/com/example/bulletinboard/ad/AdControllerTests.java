package com.example.bulletinboard.ad;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.support.TestAccounts;
import com.example.bulletinboard.support.TestAccounts.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdRepository repository;

    @Autowired
    private UserAccountRepository accountRepository;

    private User user;

    @BeforeEach
    void cleanDatabase() throws Exception {
        repository.deleteAll();
        accountRepository.deleteAll();
        user = TestAccounts.signUp(mockMvc, "Ann");
    }

    @Test
    void createsAd() throws Exception {
        mockMvc.perform(post("/api/ads")
                        .with(user.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike for sale", "description": "Almost new"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/ads/" + repository.findAll().getFirst().getId())))
                .andExpect(jsonPath("$.title").value("Bike for sale"))
                .andExpect(jsonPath("$.description").value("Almost new"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void rejectsInvalidAd() throws Exception {
        mockMvc.perform(post("/api/ads")
                        .with(user.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "description": "Almost new"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAd() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", user.id()));

        mockMvc.perform(put("/api/ads/{id}", ad.getId())
                        .with(user.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Red bike", "description": "Freshly painted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ad.getId()))
                .andExpect(jsonPath("$.title").value("Red bike"))
                .andExpect(jsonPath("$.description").value("Freshly painted"));
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingAd() throws Exception {
        mockMvc.perform(put("/api/ads/{id}", 999)
                        .with(user.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Red bike", "description": "Freshly painted"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ad 999 not found"));
    }

    @Test
    void listsAdsNewestFirst() throws Exception {
        repository.save(new Ad("First", "One"));
        repository.save(new Ad("Second", "Two"));

        mockMvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Second"));
    }

    @Test
    void allowsFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/ads")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // --- regressions for the fixed vulnerability on the legacy path (NFR5.3, NFR6.2) ---

    @Test
    void refusesAnonymousCreateOnLegacyPath() throws Exception {
        mockMvc.perform(post("/api/ads")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike for sale", "description": "Almost new"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"))
                .andExpect(header().string("Deprecation", "true"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void refusesNonOwnerEditOnLegacyPath() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", user.id()));
        User other = TestAccounts.signUp(mockMvc, "Bob");

        mockMvc.perform(put("/api/ads/{id}", ad.getId())
                        .with(other.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Hacked", "description": "Hacked"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("not_owner"));

        assertThat(repository.findById(ad.getId()).orElseThrow().getTitle()).isEqualTo("Bike");
    }

    @Test
    void keepsOldResponseShapeAndAnnouncesDeprecation() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", user.id()));

        mockMvc.perform(get("/api/ads/{id}", ad.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Link", "</api/v1/ads>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.id").value(ad.getId()))
                .andExpect(jsonPath("$.title").value("Bike"))
                .andExpect(jsonPath("$.authorName").doesNotExist())
                .andExpect(jsonPath("$.ownedByMe").doesNotExist());
    }
}
