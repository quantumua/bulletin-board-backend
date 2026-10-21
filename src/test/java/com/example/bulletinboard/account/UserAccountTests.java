package com.example.bulletinboard.account;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTests {

    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

    private final UserAccount account = new UserAccount("ann@example.com", "Ann", "$2a$12$hash", T0);

    @Test
    void fourFailuresDoNotLock() {
        failTimes(4, T0);

        assertThat(account.getFailedLoginCount()).isEqualTo(4);
        assertThat(account.isLocked(T0)).isFalse();
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void fifthFailureLocksForFifteenMinutes() {
        failTimes(5, T0);

        assertThat(account.isLocked(T0)).isTrue();
        assertThat(account.getLockedUntil()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    void lockHoldsAt14m59sAndEndsAt15m() {
        failTimes(5, T0);

        assertThat(account.isLocked(T0.plus(Duration.ofMinutes(14).plusSeconds(59)))).isTrue();
        assertThat(account.isLocked(T0.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successResetsTheCount() {
        failTimes(4, T0);

        account.recordSuccessfulLogin();

        assertThat(account.getFailedLoginCount()).isZero();
        failTimes(4, T0);
        assertThat(account.isLocked(T0)).isFalse();
    }

    @Test
    void failureAfterLockExpiryStartsANewCount() {
        failTimes(5, T0);
        Instant afterExpiry = T0.plus(Duration.ofMinutes(16));

        account.recordFailedLogin(afterExpiry);

        assertThat(account.getFailedLoginCount()).isEqualTo(1);
        assertThat(account.getLockedUntil()).isNull();
        assertThat(account.isLocked(afterExpiry)).isFalse();
    }

    @Test
    void failuresWhileLockedDoNotExtendTheLock() {
        failTimes(5, T0);
        Instant lockedUntil = account.getLockedUntil();

        account.recordFailedLogin(T0.plus(Duration.ofMinutes(10)));

        assertThat(account.getLockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void toStringNeverContainsEmailOrHash() {
        assertThat(account.toString()).doesNotContain("ann@example.com").doesNotContain("$2a$");
    }

    private void failTimes(int times, Instant now) {
        for (int i = 0; i < times; i++) {
            account.recordFailedLogin(now);
        }
    }
}
