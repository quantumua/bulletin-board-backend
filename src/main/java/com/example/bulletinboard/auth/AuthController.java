package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.AccountLockedException;
import com.example.bulletinboard.account.AccountService;
import com.example.bulletinboard.account.BadCredentialsException;
import com.example.bulletinboard.account.UserAccount;
import com.example.bulletinboard.config.ApiProblems;
import com.example.bulletinboard.config.AuthenticatedAccount;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up, log-in, log-out and "who am I" under {@code /api/v1/auth} (contract C1).
 *
 * <p>Security decisions:
 * <ul>
 *   <li>A successful sign-up or log-in always gets a new session id: an existing session's id is
 *       changed, otherwise a new session is created (session-fixation protection, NFR3.2, ADR-001).
 *       The account id becomes the principal and the security context is saved in the session.</li>
 *   <li>Log-out invalidates the session on the server, clears the security context and expires the
 *       session cookie (FR3.1, NFR3.3). It answers 204 even when nobody is logged in.</li>
 *   <li>The anti-forgery token is replaced after sign-up, log-in and log-out (NFR3.5, ADR-006); the
 *       request itself was already checked by the CSRF filter.</li>
 *   <li>Security events are logged by account id only (NFR2.4, NFR2.5).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String SESSION_COOKIE = "JSESSIONID";

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AccountService accounts;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityEventLogger securityLog;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AccountService accounts, SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository, SecurityEventLogger securityLog) {
        this.accounts = accounts;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.securityLog = securityLog;
    }

    @GetMapping("/me")
    public MeResponse me(CurrentUser caller) {
        if (!caller.isAuthenticated()) {
            return new MeResponse(null);
        }
        return new MeResponse(accounts.findAccount(caller.accountId()).map(UserResponse::from).orElse(null));
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account = accounts.register(body.email(), body.displayName(), body.password());
        try {
            startSession(account, request, response);
        } catch (RuntimeException e) {
            // The account exists; the user can log in normally (NFR1.8)
            contextHolder.clearContext();
            log.error("Session could not be started after sign-up for accountId={}", account.getId(), e);
            throw new ErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR, ApiProblems.problem(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Your account was created, but you could not be logged in. Please log in.",
                    ApiProblems.INTERNAL_ERROR), e);
        }
        securityLog.log(SecurityEventLogger.SIGNUP, account.getId(), "ok", request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(account));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        UserAccount account;
        try {
            account = accounts.authenticate(body.email(), body.password());
        } catch (BadCredentialsException e) {
            securityLog.log(SecurityEventLogger.LOGIN_FAILURE, e.getAccountId(), "bad_credentials", request);
            if (e.isAccountLockedNow()) {
                securityLog.log(SecurityEventLogger.ACCOUNT_LOCKED, e.getAccountId(), "locked", request);
            }
            throw e;
        } catch (AccountLockedException e) {
            securityLog.log(SecurityEventLogger.LOGIN_FAILURE, e.getAccountId(), "account_locked", request);
            throw e;
        }
        startSession(account, request, response);
        securityLog.log(SecurityEventLogger.LOGIN_SUCCESS, account.getId(), "ok", request);
        return UserResponse.from(account);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Long accountId = AuthenticatedAccount.idOrNull();
        new SecurityContextLogoutHandler().logout(request, response, contextHolder.getContext().getAuthentication());
        expireSessionCookie(request, response);
        rotateCsrfToken(request, response);
        securityLog.log(SecurityEventLogger.LOGOUT, accountId, "ok", request);
        return ResponseEntity.noContent().build();
    }

    private void startSession(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                account.getId(), null, AuthorityUtils.NO_AUTHORITIES));
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        rotateCsrfToken(request, response);
    }

    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken newToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(newToken, request, response);
        request.setAttribute(CsrfToken.class.getName(), newToken);
        request.setAttribute(newToken.getParameterName(), newToken);
    }

    private static void expireSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
