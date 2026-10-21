package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems.FieldMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Accounts: registration, credential verification with lockout, and display-name lookup.
 *
 * <p>Security decisions:
 * <ul>
 *   <li>Passwords are hashed with BCrypt at cost 12, with a random salt per hash (NFR1.1). The raw
 *       password is never stored, logged or returned (NFR1.2).</li>
 *   <li>BCrypt reads at most 72 bytes. A longer password is refused at sign-up rather than silently
 *       truncated, and can never match at log-in.</li>
 *   <li>An unknown email still costs one BCrypt check against a fixed dummy hash, so the response time
 *       does not reveal whether the account exists (NFR1.3).</li>
 *   <li>Lockout updates happen under a pessimistic row lock in their own short transaction, which
 *       commits even when the log-in fails, so concurrent wrong passwords are all counted (NFR3.6).
 *       Time comes from the injected {@link Clock}.</li>
 *   <li>Email uniqueness is guaranteed by a unique constraint; a violation maps to 409 (NFR1.5).</li>
 * </ul>
 */
@Service
public class AccountService {

    public static final int BCRYPT_STRENGTH = 12;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int PASSWORD_MAX_BYTES = 72;

    /**
     * Syntactic email check (FR1.2): one {@code @}, no whitespace, a dot in the domain.
     */
    public static final String EMAIL_REGEX = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final UserAccountRepository repository;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    private final String dummyHash;

    public AccountService(UserAccountRepository repository, Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        // Hash of a throw-away value, computed once, used only to equalise log-in timing
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-only");
    }

    /**
     * Normalises an email for storage and lookup: surrounding whitespace removed, lower-case (FR1.2).
     */
    public static String normaliseEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Registers a new account (FR1.1-FR1.5).
     *
     * @throws AccountValidationException when any field breaks the sign-up rules
     * @throws EmailTakenException        when the normalised email is already registered
     */
    public UserAccount register(String email, String displayName, String password) {
        String normalisedEmail = normaliseEmail(email);
        String trimmedName = displayName == null ? "" : displayName.strip();
        validateSignup(normalisedEmail, trimmedName, password);
        if (repository.findByEmail(normalisedEmail).isPresent()) {
            throw new EmailTakenException();
        }
        String hash = passwordEncoder.encode(password);
        try {
            return repository.saveAndFlush(new UserAccount(normalisedEmail, trimmedName, hash, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // A concurrent sign-up with the same email won the race (NFR1.5)
            if (repository.findByEmail(normalisedEmail).isPresent()) {
                throw new EmailTakenException();
            }
            throw e;
        }
    }

    /**
     * Verifies email and password and applies the lockout rule (FR2.1-FR2.5).
     *
     * <p>The slow BCrypt check runs before the row lock is taken; the lock state is then re-read and
     * the outcome recorded under a pessimistic write lock in a short transaction. Concurrent attempts
     * are therefore counted one after another without lost updates, and none waits on another's hash.
     *
     * @return the account on success, with its failure count reset
     * @throws BadCredentialsException when the email is unknown or the password is wrong
     * @throws AccountLockedException  when the account is locked, even with the right password
     */
    public UserAccount authenticate(String email, String password) {
        String normalisedEmail = normaliseEmail(email);
        Optional<UserAccount> found = repository.findByEmail(normalisedEmail);
        if (found.isEmpty()) {
            passwordMatches(password, dummyHash);
            throw new BadCredentialsException(null, false);
        }
        UserAccount snapshot = found.get();
        if (snapshot.isLocked(clock.instant())) {
            throw new AccountLockedException(snapshot.getId());
        }
        boolean matches = passwordMatches(password, snapshot.getPasswordHash());
        LoginOutcome outcome = transactions.execute(status -> recordLoginOutcome(normalisedEmail, matches));
        return switch (outcome) {
            case LoginOutcome.Success success -> success.account();
            case LoginOutcome.Locked locked -> throw new AccountLockedException(locked.accountId());
            case LoginOutcome.Failed failed -> throw new BadCredentialsException(failed.accountId(), failed.lockedNow());
        };
    }

    private LoginOutcome recordLoginOutcome(String normalisedEmail, boolean passwordMatches) {
        UserAccount account = repository.findByEmailForUpdate(normalisedEmail)
                .orElseThrow(() -> new IllegalStateException("Account disappeared during log-in"));
        Instant now = clock.instant();
        if (account.isLocked(now)) {
            return new LoginOutcome.Locked(account.getId());
        }
        if (!passwordMatches) {
            account.recordFailedLogin(now);
            return new LoginOutcome.Failed(account.getId(), account.isLocked(now));
        }
        account.recordSuccessfulLogin();
        return new LoginOutcome.Success(account);
    }

    private sealed interface LoginOutcome {
        record Success(UserAccount account) implements LoginOutcome {
        }

        record Locked(long accountId) implements LoginOutcome {
        }

        record Failed(long accountId, boolean lockedNow) implements LoginOutcome {
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccount(long accountId) {
        return repository.findById(accountId);
    }

    /**
     * Display names by account id, read in one query (FR5.1, NFR5.5). Unknown ids are left out.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword == null || utf8Length(rawPassword) > PASSWORD_MAX_BYTES) {
            // Such a password can never belong to an account; still spend one BCrypt check
            passwordEncoder.matches("", hash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static void validateSignup(String email, String displayName, String password) {
        List<FieldMessage> errors = new ArrayList<>();
        validateEmail(email, errors);
        validateDisplayName(displayName, errors);
        validatePassword(password, errors);
        if (!errors.isEmpty()) {
            throw new AccountValidationException(errors);
        }
    }

    private static void validateEmail(String email, List<FieldMessage> errors) {
        if (email.isEmpty()) {
            errors.add(new FieldMessage("email", "Email is required"));
        } else if (email.length() > UserAccount.EMAIL_MAX_LENGTH) {
            errors.add(new FieldMessage("email", "Email must be at most " + UserAccount.EMAIL_MAX_LENGTH + " characters"));
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new FieldMessage("email", "Email must be a valid email address"));
        }
    }

    private static void validateDisplayName(String displayName, List<FieldMessage> errors) {
        int length = displayName.codePointCount(0, displayName.length());
        if (length == 0) {
            errors.add(new FieldMessage("displayName", "Display name is required"));
        } else if (length > UserAccount.DISPLAY_NAME_MAX_LENGTH) {
            errors.add(new FieldMessage("displayName",
                    "Display name must be at most " + UserAccount.DISPLAY_NAME_MAX_LENGTH + " characters"));
        } else if (CONTROL_CHARACTERS.matcher(displayName).find()) {
            errors.add(new FieldMessage("displayName", "Display name must contain only visible characters"));
        }
    }

    private static void validatePassword(String password, List<FieldMessage> errors) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < PASSWORD_MIN_LENGTH) {
            errors.add(new FieldMessage("password", "Password must be at least " + PASSWORD_MIN_LENGTH + " characters"));
        } else if (length > PASSWORD_MAX_LENGTH) {
            errors.add(new FieldMessage("password", "Password must be at most " + PASSWORD_MAX_LENGTH + " characters"));
        } else if (utf8Length(password) > PASSWORD_MAX_BYTES) {
            errors.add(new FieldMessage("password",
                    "Password is too long: at most " + PASSWORD_MAX_BYTES + " bytes when encoded (fewer characters for non-Latin text)"));
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
