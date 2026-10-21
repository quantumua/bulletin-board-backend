package com.example.bulletinboard.ad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks every response of the legacy {@code /api/ads} paths as deprecated (contract C2):
 * {@code Deprecation: true} and {@code Link: </api/v1/ads>; rel="successor-version"}. Ordered before
 * the security filter chain so refused requests (401/403) carry the headers too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LegacyAdsDeprecationFilter extends OncePerRequestFilter {

    static final String LEGACY_PATH = "/api/ads";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals(LEGACY_PATH) || path.startsWith(LEGACY_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "</api/v1/ads>; rel=\"successor-version\"");
        filterChain.doFilter(request, response);
    }
}
