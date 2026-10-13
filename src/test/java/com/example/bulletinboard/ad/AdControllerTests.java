package com.example.bulletinboard.ad;

import com.example.bulletinboard.user.AppUser;
import com.example.bulletinboard.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdControllerTests {

    private static final String AD_JSON = """
            {"title": "Red bike", "description": "Freshly painted"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdRepository repository;

    @Autowired
    private UserRepository userRepository;

    private AppUser alice;
    private AppUser bob;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        userRepository.deleteAll();
        alice = userRepository.save(new AppUser("alice", "hash"));
        bob = userRepository.save(new AppUser("bob", "hash"));
    }

    @Test
    void createsAdOwnedByLoggedInUser() throws Exception {
        mockMvc.perform(post("/api/ads").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike for sale", "description": "Almost new", "author": "bob"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/ads/" + repository.findAll().getFirst().getId())))
                .andExpect(jsonPath("$.title").value("Bike for sale"))
                .andExpect(jsonPath("$.description").value("Almost new"))
                .andExpect(jsonPath("$.author").value("alice"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void rejectsAnonymousCreate() throws Exception {
        mockMvc.perform(post("/api/ads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").exists());

        org.junit.jupiter.api.Assertions.assertEquals(0, repository.count());
    }

    @Test
    void rejectsInvalidAd() throws Exception {
        mockMvc.perform(post("/api/ads").with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "description": "Almost new"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerUpdatesAd() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", alice));
        Thread.sleep(5);

        mockMvc.perform(put("/api/ads/{id}", ad.getId()).with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ad.getId()))
                .andExpect(jsonPath("$.title").value("Red bike"))
                .andExpect(jsonPath("$.description").value("Freshly painted"))
                .andExpect(jsonPath("$.author").value("alice"));
    }

    @Test
    void rejectsUpdateByNonOwner() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", alice));

        mockMvc.perform(put("/api/ads/{id}", ad.getId()).with(user("bob")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        org.junit.jupiter.api.Assertions.assertEquals("Bike", repository.findById(ad.getId()).orElseThrow().getTitle());
    }

    @Test
    void rejectsUpdateOfOwnerlessAd() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old"));

        mockMvc.perform(put("/api/ads/{id}", ad.getId()).with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsAnonymousUpdate() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old", alice));

        mockMvc.perform(put("/api/ads/{id}", ad.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingAd() throws Exception {
        mockMvc.perform(put("/api/ads/{id}", 999).with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ad 999 not found"));
    }

    @Test
    void anonymousCanListAdsNewestFirst() throws Exception {
        repository.save(new Ad("First", "One", alice));
        repository.save(new Ad("Second", "Two", bob));

        mockMvc.perform(get("/api/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Second"))
                .andExpect(jsonPath("$[0].author").value("bob"));
    }

    @Test
    void ownerlessAdHasNullAuthor() throws Exception {
        Ad ad = repository.save(new Ad("Legacy", "Old"));

        mockMvc.perform(get("/api/ads/{id}", ad.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void allowsFrontendOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/ads")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.equalToIgnoringCase("content-type, x-xsrf-token")));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/ads")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
