package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.UserAccount;

/**
 * The logged-in user as returned to that user only (contract C1 {@code CurrentUser}). Never contains
 * the password hash.
 */
public record UserResponse(Long id, String email, String displayName) {

    static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getEmail(), account.getDisplayName());
    }
}
