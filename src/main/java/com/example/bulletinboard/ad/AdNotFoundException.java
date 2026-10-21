package com.example.bulletinboard.ad;

import com.example.bulletinboard.config.ApiProblems;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AdNotFoundException extends ErrorResponseException {

    public AdNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, ApiProblems.problem(HttpStatus.NOT_FOUND, "Ad " + id + " not found", ApiProblems.AD_NOT_FOUND), null);
    }
}
