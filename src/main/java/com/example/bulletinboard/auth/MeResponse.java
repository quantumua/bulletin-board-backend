package com.example.bulletinboard.auth;

/**
 * Answer of {@code GET /api/v1/auth/me}: the current user, or {@code null} for a visitor (FR3.3).
 */
public record MeResponse(UserResponse user) {
}
