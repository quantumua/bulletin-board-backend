package com.example.bulletinboard.ad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
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

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsAd() throws Exception {
        mockMvc.perform(post("/api/ads")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "description": "Almost new"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAd() throws Exception {
        Ad ad = repository.save(new Ad("Bike", "Old"));

        mockMvc.perform(put("/api/ads/{id}", ad.getId())
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
}
