package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Sign-up with an email that already belongs to an account (FR1.3): 409 {@code email_taken}.
 */
public class EmailTakenException extends ErrorResponseException {

    public EmailTakenException() {
        super(HttpStatus.CONFLICT,
                ApiProblems.problem(HttpStatus.CONFLICT, "Email already registered", ApiProblems.EMAIL_TAKEN), null);
    }
}
