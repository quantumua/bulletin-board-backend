package com.example.bulletinboard.config;

import com.example.bulletinboard.auth.NotAuthenticatedException;
import com.example.bulletinboard.auth.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces Spring Security's default 401/403 responses with RFC 9457 problem details carrying a
 * {@code code} (NFR7.2): {@code not_authenticated} (401), {@code csrf_invalid} or {@code forbidden}
 * (403). Refused writes are recorded as {@code write_refused} security events.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String CSRF_DETAIL = "Invalid or missing anti-forgery token";
    static final String FORBIDDEN_DETAIL = "Access denied";

    private final JsonMapper jsonMapper;
    private final SecurityEventLogger securityLog;

    public SecurityProblemHandler(JsonMapper jsonMapper, SecurityEventLogger securityLog) {
        this.jsonMapper = jsonMapper;
        this.securityLog = securityLog;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        logRefusedWrite(request, null, "anonymous");
        write(request, response, HttpStatus.UNAUTHORIZED, NotAuthenticatedException.DETAIL, ApiProblems.NOT_AUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            logRefusedWrite(request, AuthenticatedAccount.idOrNull(), "csrf");
            write(request, response, HttpStatus.FORBIDDEN, CSRF_DETAIL, ApiProblems.CSRF_INVALID);
        } else {
            logRefusedWrite(request, AuthenticatedAccount.idOrNull(), "forbidden");
            write(request, response, HttpStatus.FORBIDDEN, FORBIDDEN_DETAIL, ApiProblems.FORBIDDEN);
        }
    }

    private void logRefusedWrite(HttpServletRequest request, Long accountId, String outcome) {
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method)) {
            securityLog.log(SecurityEventLogger.WRITE_REFUSED, accountId, outcome, request);
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail, String code)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put(ApiProblems.CODE, code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
