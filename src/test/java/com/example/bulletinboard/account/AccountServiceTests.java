package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems.FieldMessage;
import com.example.bulletinboard.support.MutableClock;
import com.example.bulletinboard.support.MutableClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@Import(MutableClockConfig.class)
class AccountServiceTests {

    private static final String PASSWORD = "correct-horse";

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        clock.reset();
    }

    @Test
    void registersWithNormalisedEmailTrimmedNameAndBcrypt12Hash() {
        UserAccount account = accountService.register("  Ann@Example.COM ", "  Ann  ", PASSWORD);

        UserAccount stored = repository.findById(account.getId()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("ann@example.com");
        assertThat(stored.getDisplayName()).isEqualTo("Ann");
        assertThat(stored.getPasswordHash()).startsWith("$2a$12$").doesNotContain(PASSWORD);
        assertThat(stored.getCreatedAt()).isEqualTo(MutableClock.START);
    }

    @Test
    void samePasswordGivesDifferentHashes() {
        UserAccount ann = accountService.register("ann@example.com", "Ann", PASSWORD);
        UserAccount bob = accountService.register("bob@example.com", "Bob", PASSWORD);

        assertThat(ann.getPasswordHash()).isNotEqualTo(bob.getPasswordHash());
    }

    @Test
    void duplicateEmailInAnyCaseOrSpacingIsRefusedWith409() {
        accountService.register("ann@example.com", "Ann", PASSWORD);

        EmailTakenException refused = catchThrowableOfType(EmailTakenException.class,
                () -> accountService.register("Ann@Example.com ", "Another Ann", PASSWORD));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().getProperties()).containsEntry("code", "email_taken");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentSignUpsWithTheSameEmailCreateExactlyOneAccount() throws Exception {
        List<Callable<Object>> attempts = List.of(
                () -> accountService.register("race@example.com", "One", PASSWORD),
                () -> accountService.register("RACE@example.com", "Two", PASSWORD));

        List<Throwable> failures = runInParallel(attempts);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(failures).hasSize(1).allMatch(EmailTakenException.class::isInstance);
    }

    @Test
    void passwordsOf7And129CharactersAreRefused() {
        assertPasswordRefused("a".repeat(7));
        assertPasswordRefused("a".repeat(129));
        assertThat(repository.count()).isZero();
    }

    @Test
    void multiBytePasswordOver72BytesIsRefusedAsValidationError() {
        String fortyTwoByteChars = "é".repeat(40);
        assertThat(fortyTwoByteChars).hasSize(40);
        assertThat(fortyTwoByteChars.getBytes(StandardCharsets.UTF_8)).hasSize(80);

        assertPasswordRefused(fortyTwoByteChars);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456789012345678901234567890123456789012345678901"})
    void invalidDisplayNamesAreRefused(String displayName) {
        AccountValidationException refused = catchThrowableOfType(AccountValidationException.class,
                () -> accountService.register("ann@example.com", displayName, PASSWORD));

        assertThat(refused.getErrors()).extracting(FieldMessage::field).containsExactly("displayName");
        assertThat(repository.count()).isZero();
    }

    @Test
    void invalidEmailIsRefused() {
        AccountValidationException refused = catchThrowableOfType(AccountValidationException.class,
                () -> accountService.register("not-an-email", "Ann", PASSWORD));

        assertThat(refused.getErrors()).extracting(FieldMessage::field).containsExactly("email");
    }

    @Test
    void authenticateSucceedsWithTheRightPassword() {
        UserAccount registered = accountService.register("ann@example.com", "Ann", PASSWORD);

        UserAccount authenticated = accountService.authenticate(" ANN@example.com", PASSWORD);

        assertThat(authenticated.getId()).isEqualTo(registered.getId());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameBadCredentials() {
        accountService.register("ann@example.com", "Ann", PASSWORD);

        BadCredentialsException wrongPassword = catchThrowableOfType(BadCredentialsException.class,
                () -> accountService.authenticate("ann@example.com", "wrong-password"));
        BadCredentialsException unknownEmail = catchThrowableOfType(BadCredentialsException.class,
                () -> accountService.authenticate("nobody@example.com", PASSWORD));

        assertThat(wrongPassword.getBody().getDetail()).isEqualTo(unknownEmail.getBody().getDetail())
                .isEqualTo("Email or password is incorrect");
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getAccountId()).isNull();
    }

    @Test
    void overLongPasswordAtLogInIsBadCredentialsNotAnError() {
        accountService.register("ann@example.com", "Ann", PASSWORD);

        assertThatThrownBy(() -> accountService.authenticate("ann@example.com", "x".repeat(200)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void locksAfterFiveFailuresAndUnlocksAfterFifteenMinutes() {
        accountService.register("ann@example.com", "Ann", PASSWORD);
        for (int i = 1; i <= 4; i++) {
            BadCredentialsException failure = catchThrowableOfType(BadCredentialsException.class,
                    () -> accountService.authenticate("ann@example.com", "wrong-password"));
            assertThat(failure.isAccountLockedNow()).isFalse();
        }
        BadCredentialsException fifth = catchThrowableOfType(BadCredentialsException.class,
                () -> accountService.authenticate("ann@example.com", "wrong-password"));
        assertThat(fifth.isAccountLockedNow()).isTrue();

        assertThatThrownBy(() -> accountService.authenticate("ann@example.com", PASSWORD))
                .isInstanceOf(AccountLockedException.class);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThatThrownBy(() -> accountService.authenticate("ann@example.com", PASSWORD))
                .isInstanceOf(AccountLockedException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThat(accountService.authenticate("ann@example.com", PASSWORD).getFailedLoginCount()).isZero();
    }

    @Test
    void tenParallelWrongAttemptsEndLocked() throws Exception {
        accountService.register("ann@example.com", "Ann", PASSWORD);
        List<Callable<Object>> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            attempts.add(() -> accountService.authenticate("ann@example.com", "wrong-password"));
        }

        List<Throwable> failures = runInParallel(attempts);

        assertThat(failures).hasSize(10)
                .allMatch(f -> f instanceof BadCredentialsException || f instanceof AccountLockedException);
        assertThat(failures).filteredOn(BadCredentialsException.class::isInstance).hasSize(5);
        UserAccount stored = repository.findByEmail("ann@example.com").orElseThrow();
        assertThat(stored.isLocked(clock.instant())).isTrue();
        assertThatThrownBy(() -> accountService.authenticate("ann@example.com", PASSWORD))
                .isInstanceOf(AccountLockedException.class);
    }

    private void assertPasswordRefused(String password) {
        AccountValidationException refused = catchThrowableOfType(AccountValidationException.class,
                () -> accountService.register("ann@example.com", "Ann", password));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().getProperties()).containsEntry("code", "validation_failed");
        assertThat(refused.getErrors()).extracting(FieldMessage::field).containsExactly("password");
    }

    private static List<Throwable> runInParallel(List<Callable<Object>> tasks) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            for (Future<Object> future : executor.invokeAll(tasks)) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        }
        return failures;
    }
}
