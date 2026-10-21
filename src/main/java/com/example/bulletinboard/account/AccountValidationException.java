package com.example.bulletinboard.account;

import com.example.bulletinboard.config.ApiProblems;
import com.example.bulletinboard.config.ApiProblems.FieldMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.util.List;

/**
 * Sign-up input refused by the account rules (FR1.2, FR1.4, FR1.5, FR1.7): 400
 * {@code validation_failed} with one {@code errors} entry per wrong field.
 */
public class AccountValidationException extends ErrorResponseException {

    private final List<FieldMessage> errors;

    public AccountValidationException(List<FieldMessage> errors) {
        super(HttpStatus.BAD_REQUEST, ApiProblems.validationProblem(errors), null);
        this.errors = List.copyOf(errors);
    }

    public List<FieldMessage> getErrors() {
        return errors;
    }
}
