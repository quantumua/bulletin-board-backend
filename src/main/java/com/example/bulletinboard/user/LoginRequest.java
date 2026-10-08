package com.example.bulletinboard.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = User.EMAIL_MAX_LENGTH) String email,
        @NotBlank @Size(max = RegisterRequest.PASSWORD_MAX_BYTES) String password
) {
    public LoginRequest {
        // Surrounding whitespace is ignored, so it must not fail @Email validation
        email = email == null ? null : email.trim();
    }
}
