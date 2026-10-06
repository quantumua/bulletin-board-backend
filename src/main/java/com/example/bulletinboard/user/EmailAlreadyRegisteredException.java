package com.example.bulletinboard.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class EmailAlreadyRegisteredException extends ErrorResponseException {

    public EmailAlreadyRegisteredException(Throwable cause) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email is already registered"), cause);
    }
}
