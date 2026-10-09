package com.example.bulletinboard.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class UsernameTakenException extends ErrorResponseException {

    public UsernameTakenException(String username) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Username '" + username + "' is already taken"), null);
    }
}
