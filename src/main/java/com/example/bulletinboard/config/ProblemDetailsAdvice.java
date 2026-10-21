package com.example.bulletinboard.config;

import com.example.bulletinboard.ad.NotOwnerException;
import com.example.bulletinboard.ad.UnownedAdException;
import com.example.bulletinboard.auth.SecurityEventLogger;
import com.example.bulletinboard.config.ApiProblems.FieldMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Every error answered by the API is RFC 9457 problem details with a {@code code} (NFR7.1):
 * <ul>
 *   <li>Bean Validation failures: 400 {@code validation_failed} with {@code errors[]} of {@code field}/{@code message};</li>
 *   <li>malformed JSON and a non-numeric id: 400 {@code bad_request};</li>
 *   <li>unknown paths and other 404s: {@code not_found}; 405 {@code method_not_allowed}; 415 {@code unsupported_media_type};</li>
 *   <li>our own {@link ErrorResponseException}s keep the code they carry;</li>
 *   <li>anything unexpected: 500 {@code internal_error}, logged at ERROR with the exception and the path
 *       but never the request body, and with no internal details in the response (NFR7.3, NFR7.4).</li>
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    private final SecurityEventLogger securityLog;

    public ProblemDetailsAdvice(SecurityEventLogger securityLog) {
        this.securityLog = securityLog;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldMessage> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldMessage(error.getField(), error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, ApiProblems.validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleErrorResponseException(ErrorResponseException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (ex instanceof NotOwnerException || ex instanceof UnownedAdException) {
            Object code = ex.getBody().getProperties() == null ? null : ex.getBody().getProperties().get(ApiProblems.CODE);
            securityLog.log(SecurityEventLogger.WRITE_REFUSED, AuthenticatedAccount.idOrNull(), String.valueOf(code),
                    servletRequest(request));
        }
        return super.handleErrorResponseException(ex, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addCodeIfMissing(problem, statusCode);
        }
        return response;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiProblems.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", ApiProblems.INTERNAL_ERROR));
    }

    private static void addCodeIfMissing(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getProperties() != null && problem.getProperties().containsKey(ApiProblems.CODE)) {
            return;
        }
        problem.setProperty(ApiProblems.CODE, codeFor(status));
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ApiProblems.NOT_AUTHENTICATED;
            case 403 -> ApiProblems.FORBIDDEN;
            case 404 -> ApiProblems.NOT_FOUND;
            case 405 -> ApiProblems.METHOD_NOT_ALLOWED;
            case 415 -> ApiProblems.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ApiProblems.INTERNAL_ERROR : ApiProblems.BAD_REQUEST;
        };
    }

    private static @Nullable HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest ? servletWebRequest.getRequest() : null;
    }
}
