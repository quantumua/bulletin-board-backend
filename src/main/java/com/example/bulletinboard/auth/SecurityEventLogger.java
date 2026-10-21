package com.example.bulletinboard.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes one INFO line per security event to the {@code security} logger (NFR2.4):
 * {@code event=<name> accountId=<id or none> outcome=<ok or reason> path=<path>}.
 *
 * <p>Accounts are identified by id only. Email, password, session id and anti-forgery token are never
 * logged (NFR2.2, NFR2.5).
 */
@Component
public class SecurityEventLogger {

    public static final String SIGNUP = "signup";
    public static final String LOGIN_SUCCESS = "login_success";
    public static final String LOGIN_FAILURE = "login_failure";
    public static final String ACCOUNT_LOCKED = "account_locked";
    public static final String LOGOUT = "logout";
    public static final String WRITE_REFUSED = "write_refused";

    private static final Logger log = LoggerFactory.getLogger("security");

    public void log(String event, Long accountId, String outcome, HttpServletRequest request) {
        log.info("event={} accountId={} outcome={} path={}", event, accountId == null ? "none" : accountId,
                outcome, safePath(request));
    }

    private static String safePath(HttpServletRequest request) {
        if (request == null) {
            return "none";
        }
        // Path only (no query string), with control characters removed to keep one line per event
        return request.getRequestURI().replaceAll("\\p{Cntrl}", "");
    }
}
