package com.example.bulletinboard.ad;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.support.TestAccounts;
import com.example.bulletinboard.support.TestAccounts.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Targeted regressions for the fixed vulnerability (security-patch floor, NFR6.2): anonymous and
 * non-owner writes are refused through the API itself, and the owner is never taken from the body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdV1ControllerTests {

    private static final String AD_JSON = """
            {"title": "Bike for sale", "description": "Almost new"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private AdRepository adRepository;

    @Autowired
    private UserAccountRepository accountRepository;

    private User ann;
    private User bob;

    @BeforeEach
    void setUp() throws Exception {
        reset(adRepository);
        adRepository.deleteAll();
        accountRepository.deleteAll();
        ann = TestAccounts.signUp(mockMvc, "Ann");
        bob = TestAccounts.signUp(mockMvc, "Bob");
    }

    @Test
    void anonymousCreateWithTokenIsRefusedWith401AndNoAdIsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/ads")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not_authenticated"))
                .andExpect(jsonPath("$.detail").value("You need to log in"));

        assertThat(adRepository.count()).isZero();
    }

    @Test
    void anonymousCreateWithoutTokenIsRefusedWith403AndNoAdIsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/ads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));

        assertThat(adRepository.count()).isZero();
    }

    @Test
    void anonymousEditIsRefusedWith401AndTheAdIsUnchanged(CapturedOutput output) throws Exception {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.id()));

        mockMvc.perform(put("/api/v1/ads/{id}", ad.getId())
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("not_authenticated"));

        assertThat(adRepository.findById(ad.getId()).orElseThrow().getTitle()).isEqualTo("Bike");
        assertThat(output.getAll()).contains("event=write_refused accountId=none outcome=anonymous");
    }

    @Test
    void otherUsersEditIsRefusedWith403NotOwnerAndTheAdIsUnchanged(CapturedOutput output) throws Exception {
        Ad ad = adRepository.save(new Ad("Bike", "Old", ann.id()));

        mockMvc.perform(put("/api/v1/ads/{id}", ad.getId())
                        .with(bob.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("not_owner"))
                .andExpect(jsonPath("$.detail").value("You can only edit your own ads"));

        Ad unchanged = adRepository.findById(ad.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Bike");
        assertThat(unchanged.getDescription()).isEqualTo("Old");
        assertThat(output.getAll()).contains("event=write_refused accountId=" + bob.id() + " outcome=not_owner");
    }

    @Test
    void ownerCanCreateAndEdit() throws Exception {
        String location = mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/ads/")))
                .andExpect(jsonPath("$.authorName").value("Ann"))
                .andExpect(jsonPath("$.ownedByMe").value(true))
                .andReturn().getResponse().getHeader("Location");
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(put("/api/v1/ads/{id}", id)
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Red bike", "description": "Freshly painted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Red bike"))
                .andExpect(jsonPath("$.ownedByMe").value(true));

        assertThat(adRepository.findById(id).orElseThrow().getOwnerId()).isEqualTo(ann.id());
    }

    @Test
    void ownerFieldsInTheBodyAreIgnored() throws Exception {
        String created = mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike", "description": "New", "ownerId": %d, "owner": %d}
                                """.formatted(bob.id(), bob.id())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
        assertThat(adRepository.findById(id).orElseThrow().getOwnerId()).isEqualTo(ann.id());

        mockMvc.perform(put("/api/v1/ads/{id}", id)
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bike", "description": "Edited", "ownerId": %d}
                                """.formatted(bob.id())))
                .andExpect(status().isOk());

        assertThat(adRepository.findById(id).orElseThrow().getOwnerId()).isEqualTo(ann.id());
    }

    @Test
    void editingAnUnownedAdIsRefusedWith403Unowned() throws Exception {
        Ad ad = adRepository.save(new Ad("Old ad", "From before login"));

        mockMvc.perform(put("/api/v1/ads/{id}", ad.getId())
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("unowned"))
                .andExpect(jsonPath("$.detail").value("This ad has no owner and cannot be edited"));
    }

    @Test
    void listAndViewAreOpenWithAuthorNamesAndNoEmail() throws Exception {
        Ad annsAd = adRepository.save(new Ad("Ann's", "A", ann.id()));
        adRepository.save(new Ad("Unowned", "Old"));

        String list = mockMvc.perform(get("/api/v1/ads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].authorName").value("Unknown"))
                .andExpect(jsonPath("$[1].authorName").value("Ann"))
                .andExpect(jsonPath("$[1].ownedByMe").value(false))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/ads/{id}", annsAd.getId()).with(ann.loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Ann"))
                .andExpect(jsonPath("$.ownedByMe").value(true))
                .andExpect(content().string(not(containsString("@"))));

        assertThat(list).doesNotContain("@").doesNotContain(ann.email());
    }

    @Test
    void invalidBodyFromALoggedInUserIsRefusedWith400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "description": "Almost new"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void malformedJsonAndNonNumericIdAreRefusedWith400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
        mockMvc.perform(get("/api/v1/ads/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void missingAdAndUnknownPathAreProblemDetails404() throws Exception {
        mockMvc.perform(get("/api/v1/ads/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ad_not_found"))
                .andExpect(jsonPath("$.detail").value("Ad 999 not found"));
        mockMvc.perform(get("/api/v1/ads/1/comments"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void wrongMethodAndMediaTypeAreProblemDetails() throws Exception {
        mockMvc.perform(put("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method_not_allowed"));
        mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Bike"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));
    }

    @Test
    void storageFailureIsA500ProblemWithoutInternalsAndIsLogged(CapturedOutput output) throws Exception {
        doThrow(new DataAccessResourceFailureException("database is down: secret-internal-detail"))
                .when(adRepository).findAllByOrderByCreatedAtDescIdDesc();

        mockMvc.perform(get("/api/v1/ads"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));

        assertThat(output.getAll()).contains("ERROR").contains("Unexpected error on GET /api/v1/ads");
    }

    @Test
    void createAnswersWithLocationOfTheNewAd() throws Exception {
        mockMvc.perform(post("/api/v1/ads")
                        .with(ann.loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AD_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/ads/" + adRepository.findAll().getFirst().getId())));
    }
}
