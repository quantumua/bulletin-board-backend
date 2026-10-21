package com.example.bulletinboard.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.List;

/**
 * Builds RFC 9457 problem details carrying the stable machine-readable {@code code} extension
 * (NFR7.1). Clients key their behaviour on {@code code}, never on the {@code detail} text.
 */
public final class ApiProblems {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    public static final String VALIDATION_FAILED = "validation_failed";
    public static final String NOT_AUTHENTICATED = "not_authenticated";
    public static final String BAD_CREDENTIALS = "bad_credentials";
    public static final String ACCOUNT_LOCKED = "account_locked";
    public static final String EMAIL_TAKEN = "email_taken";
    public static final String CSRF_INVALID = "csrf_invalid";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_OWNER = "not_owner";
    public static final String UNOWNED = "unowned";
    public static final String AD_NOT_FOUND = "ad_not_found";
    public static final String NOT_FOUND = "not_found";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String BAD_REQUEST = "bad_request";
    public static final String INTERNAL_ERROR = "internal_error";

    public static final String VALIDATION_FAILED_DETAIL = "Validation failed";

    private ApiProblems() {
    }

    public static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }

    public static ProblemDetail validationProblem(List<FieldMessage> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_DETAIL, VALIDATION_FAILED);
        problem.setProperty(ERRORS, List.copyOf(errors));
        return problem;
    }

    /**
     * One entry of the {@code errors} array of a validation problem.
     */
    public record FieldMessage(String field, String message) {
    }
}
