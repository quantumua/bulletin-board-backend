package com.example.bulletinboard.ad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class AdNotFoundException extends ErrorResponseException {

    public AdNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Ad " + id + " not found"), null);
    }
}
