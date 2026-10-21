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
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.bulletinboard.support.TestAccounts.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security events that no other test asserts (NFR2.4, NFR2.5; Build and Test addition): the lock is
 * logged once as {@code account_locked}, a log-in refused by the lock is a {@code login_failure} with
 * outcome {@code account_locked}, an unknown email is logged with {@code accountId=none}, and a write
 * refused by the anti-forgery check is a {@code write_refused} with outcome {@code csrf}. No line
 * carries the email or the password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityEventLogTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AdRepository adRepository;

    @BeforeEach
    void cleanDatabase() {
        adRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void lockIsLoggedOnceAndARefusedLogInIsALoginFailure(CapturedOutput output) throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");
        for (int i = 0; i < 5; i++) {
            logIn(ann.email(), "wrong-password").andExpect(status().isUnauthorized());
        }
        logIn(ann.email(), ann.password()).andExpect(status().isLocked());

        String log = output.getAll();
        assertThat(count(log, "event=account_locked accountId=" + ann.id() + " outcome=locked path=/api/v1/auth/login"))
                .isEqualTo(1);
        assertThat(count(log, "event=login_failure accountId=" + ann.id() + " outcome=bad_credentials")).isEqualTo(5);
        assertThat(count(log, "event=login_failure accountId=" + ann.id() + " outcome=account_locked")).isEqualTo(1);
        assertThat(log).doesNotContain(ann.email()).doesNotContain(ann.password());
    }

    @Test
    void unknownEmailIsLoggedWithoutAnAccountOrTheEmail(CapturedOutput output) throws Exception {
        String email = TestAccounts.uniqueEmail();
        logIn(email, "wrong-password").andExpect(status().isUnauthorized());

        assertThat(output.getAll())
                .contains("event=login_failure accountId=none outcome=bad_credentials path=/api/v1/auth/login")
                .doesNotContain(email);
    }

    @Test
    void writeRefusedByTheTokenCheckIsLoggedAsCsrf(CapturedOutput output) throws Exception {
        User ann = TestAccounts.signUp(mockMvc, "Ann");
        mockMvc.perform(post("/api/v1/ads")
                        .session(ann.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Bike\", \"description\": \"New\"}"))
                .andExpect(status().isForbidden());

        assertThat(output.getAll()).contains("event=write_refused accountId=" + ann.id() + " outcome=csrf path=/api/v1/ads");
        assertThat(adRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions logIn(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(xsrf(TestAccounts.fetchXsrfCookie(mockMvc)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private static int count(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }
}
