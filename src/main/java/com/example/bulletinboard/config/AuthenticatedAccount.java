package com.example.bulletinboard.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the logged-in account id from the security context. The principal of an authenticated
 * session is the account id ({@link Long}), set at sign-up and log-in.
 */
public final class AuthenticatedAccount {

    private AuthenticatedAccount() {
    }

    /**
     * The caller's account id, or {@code null} when the caller is anonymous.
     */
    public static Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof Long accountId ? accountId : null;
    }
}
