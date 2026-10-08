package com.example.bulletinboard.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = User.EMAIL_MAX_LENGTH) String email,
        @NotNull @Size(min = PASSWORD_MIN_LENGTH) @MaxUtf8Bytes(PASSWORD_MAX_BYTES) String password
) {
    public static final int PASSWORD_MIN_LENGTH = 8;
    // bcrypt rejects input beyond 72 bytes, so non-ASCII passwords allow fewer than 72 characters
    public static final int PASSWORD_MAX_BYTES = 72;

    public RegisterRequest {
        // Surrounding whitespace is ignored, so it must not fail @Email validation
        email = email == null ? null : email.trim();
    }
}
