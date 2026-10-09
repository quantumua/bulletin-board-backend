package com.example.bulletinboard.ad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class AdForbiddenException extends ErrorResponseException {

    public AdForbiddenException(long id) {
        super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Only the owner can edit ad " + id), null);
    }
}
