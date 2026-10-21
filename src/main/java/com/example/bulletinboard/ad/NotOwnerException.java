package com.example.bulletinboard.ad;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * A logged-in user tried to edit someone else's ad (FR4.3): 403 {@code not_owner}.
 */
public class NotOwnerException extends ErrorResponseException {

    public NotOwnerException() {
        super(HttpStatus.FORBIDDEN,
                ApiProblems.problem(HttpStatus.FORBIDDEN, "You can only edit your own ads", ApiProblems.NOT_OWNER), null);
    }
}
