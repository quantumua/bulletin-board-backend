package com.example.bulletinboard.auth;

import jakarta.validation.constraints.NotNull;

/**
 * Log-in body (FR2.1). Only presence is validated; a wrong value of any shape is answered with the
 * generic bad-credentials problem (FR2.2).
 */
public record LoginRequest(@NotNull String email, @NotNull String password) {

    @Override
    public String toString() {
        // Never print the password or the email (NFR1.2, NFR2.2)
        return "LoginRequest[...]";
    }
}
