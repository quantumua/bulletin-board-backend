package com.example.bulletinboard.auth;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * A protected operation reached by an anonymous caller: 401 {@code not_authenticated}. The security
 * filter chain normally refuses such requests first; this is the service-level backstop.
 */
public class NotAuthenticatedException extends ErrorResponseException {

    public static final String DETAIL = "You need to log in";

    public NotAuthenticatedException() {
        super(HttpStatus.UNAUTHORIZED,
                ApiProblems.problem(HttpStatus.UNAUTHORIZED, DETAIL, ApiProblems.NOT_AUTHENTICATED), null);
    }
}
