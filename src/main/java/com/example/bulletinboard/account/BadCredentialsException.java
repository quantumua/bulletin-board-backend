package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Wrong email or password (FR2.2): 401 {@code bad_credentials} with one generic message that does
 * not reveal whether the email exists.
 */
public class BadCredentialsException extends ErrorResponseException {

    public static final String DETAIL = "Email or password is incorrect";

    private final Long accountId;
    private final boolean accountLockedNow;

    public BadCredentialsException(Long accountId, boolean accountLockedNow) {
        super(HttpStatus.UNAUTHORIZED,
                ApiProblems.problem(HttpStatus.UNAUTHORIZED, DETAIL, ApiProblems.BAD_CREDENTIALS), null);
        this.accountId = accountId;
        this.accountLockedNow = accountLockedNow;
    }

    /**
     * Account id for security logging only, or {@code null} when the email is unknown.
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * Whether this failure was the one that locked the account.
     */
    public boolean isAccountLockedNow() {
        return accountLockedNow;
    }
}
