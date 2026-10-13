package com.example.bulletinboard.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Deliberately identical for an unknown username and a wrong password. */
public class InvalidCredentialsException extends ErrorResponseException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password"), null);
    }
}
