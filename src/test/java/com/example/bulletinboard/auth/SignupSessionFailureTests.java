package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.ad.AdRepository;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR1.8 (Build and Test addition): sign-up is all or nothing from the user's point of view. When the
 * session cannot be started after the account was saved, the answer is a 500 problem telling the user
 * to log in, the account exists, the half-started session is anonymous, and a later log-in succeeds.
 *
 * <p>The failure is simulated by making the real security-context repository throw once; production
 * code is not changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SignupSessionFailureTests {

    private static final String RECOVERY_DETAIL =
            "Your account was created, but you could not be logged in. Please log in.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AdRepository adRepository;

    @MockitoSpyBean
    private SecurityContextRepository securityContextRepository;

    @BeforeEach
    void cleanDatabase() {
        reset(securityContextRepository);
        adRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void sessionFailureAfterSignupAnswers500PleaseLogInAndTheAccountCanLogInLater(CapturedOutput output)
            throws Exception {
        String email = TestAccounts.uniqueEmail();
        String password = TestAccounts.markerPassword();
        doThrow(new IllegalStateException("simulated session store failure"))
                .when(securityContextRepository).saveContext(any(), any(), any());

        MvcResult failed = mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "Ann", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value(RECOVERY_DETAIL))
                .andExpect(content().string(not(containsString(password))))
                .andExpect(content().string(not(containsString("simulated session store failure"))))
                .andReturn();

        // The account exists, so the email is not blocked by a half-created account
        assertThat(accountRepository.findByEmail(email)).isPresent();
        assertThat(accountRepository.count()).isEqualTo(1);

        // Whatever session the failed request left behind is anonymous
        MockHttpSession leftOver = (MockHttpSession) failed.getRequest().getSession(false);
        if (leftOver != null) {
            mockMvc.perform(get("/api/v1/auth/me").session(leftOver))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user").value(nullValue()));
        }

        // The failure is logged at ERROR by account id, never with the password or email
        assertThat(output.getAll())
                .contains("ERROR")
                .contains("Session could not be started after sign-up for accountId=")
                .doesNotContain(password)
                .doesNotContain("event=signup accountId=");

        // Once the session store works again, the same credentials log in
        reset(securityContextRepository);
        User ann = TestAccounts.logIn(mockMvc, email, password);
        mockMvc.perform(get("/api/v1/auth/me").session(ann.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Ann"));
    }

    @Test
    void signingUpAgainAfterTheFailureIsRefusedAsDuplicateNotAsAServerError() throws Exception {
        String email = TestAccounts.uniqueEmail();
        doThrow(new IllegalStateException("simulated session store failure"))
                .when(securityContextRepository).saveContext(any(), any(), any());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "Ann", "password": "%s"}
                                """.formatted(email, TestAccounts.markerPassword())))
                .andExpect(status().isInternalServerError());
        reset(securityContextRepository);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "displayName": "Ann", "password": "%s"}
                                """.formatted(email, TestAccounts.markerPassword())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void normalSignupStillLogsInWhenTheSessionStoreWorks() throws Exception {
        User bob = TestAccounts.signUp(mockMvc, "Bob");

        mockMvc.perform(get("/api/v1/auth/me").session(bob.session()))
                .andExpect(jsonPath("$.user.id").value(bob.id()));
    }
}
