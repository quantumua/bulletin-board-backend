package com.example.bulletinboard.auth;

import com.example.bulletinboard.user.AppUser;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull @Size(min = AppUser.USERNAME_MIN_LENGTH, max = AppUser.USERNAME_MAX_LENGTH) String username,
        @NotNull @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH) String password
) {
    public static final int PASSWORD_MIN_LENGTH = 8;
    // bcrypt only uses the first 72 bytes of its input
    public static final int PASSWORD_MAX_LENGTH = 72;
}
