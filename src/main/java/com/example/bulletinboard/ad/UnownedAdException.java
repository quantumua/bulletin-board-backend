package com.example.bulletinboard.ad;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * An edit of an ad that has no owner, which nobody may edit (FR4.5): 403 {@code unowned}.
 */
public class UnownedAdException extends ErrorResponseException {

    public UnownedAdException() {
        super(HttpStatus.FORBIDDEN, ApiProblems.problem(HttpStatus.FORBIDDEN,
                "This ad has no owner and cannot be edited", ApiProblems.UNOWNED), null);
    }
}
