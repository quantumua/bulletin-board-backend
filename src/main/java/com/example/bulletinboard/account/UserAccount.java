package com.example.bulletinboard.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered user. The email is stored in normalised form (trimmed, lower-case) and is unique.
 * Only the BCrypt hash of the password is kept.
 *
 * <p>The entity owns the lockout rule (FR2.3-FR2.5): the 5th consecutive failed log-in locks the
 * account for 15 minutes; once the lock has expired the account can log in again, and a new failure
 * starts a new count; a successful log-in resets the count.
 */
@Entity
@Table(name = "user_accounts",
        uniqueConstraints = @UniqueConstraint(name = UserAccount.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
public class UserAccount {

    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_user_accounts_email";
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int DISPLAY_NAME_MAX_LENGTH = 50;
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private int failedLoginCount;

    private Instant lockedUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, Instant createdAt) {
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Whether the account is locked at {@code now}. The lock ends exactly at {@code lockedUntil}.
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts one failed log-in. The 5th consecutive failure locks the account for 15 minutes.
     * A failure after an expired lock starts a new count. While locked, nothing changes, so the
     * lock is never extended by further attempts.
     */
    public void recordFailedLogin(Instant now) {
        if (isLocked(now)) {
            return;
        }
        if (lockedUntil != null) {
            clearLockout();
        }
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    /**
     * Resets the failure count and any expired lock after a successful log-in.
     */
    public void recordSuccessfulLogin() {
        clearLockout();
    }

    private void clearLockout() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        // Never include the email or the password hash (NFR2.2, NFR2.3)
        return "UserAccount[id=" + id + "]";
    }
}
