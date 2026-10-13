package com.example.bulletinboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security loads the CSRF token lazily; reading it here makes every response carry the
 * readable {@code XSRF-TOKEN} cookie so the frontend always has a token to echo.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    // The user-directory contract says GET /api/users sets no cookies, which includes XSRF-TOKEN
    private static final String COOKIE_FREE_PATH = "/api/users";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null && !request.getRequestURI().equals(COOKIE_FREE_PATH)) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
