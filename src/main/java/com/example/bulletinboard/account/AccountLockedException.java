package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Log-in to an account that is temporarily locked (FR2.3): 423 {@code account_locked}.
 */
public class AccountLockedException extends ErrorResponseException {

    private final long accountId;

    public AccountLockedException(long accountId) {
        super(HttpStatus.LOCKED, ApiProblems.problem(HttpStatus.LOCKED,
                "Account temporarily locked. Try again later.", ApiProblems.ACCOUNT_LOCKED), null);
        this.accountId = accountId;
    }

    /**
     * Account id for security logging only; never part of the response.
     */
    public long getAccountId() {
        return accountId;
    }
}
