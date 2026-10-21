package com.example.bulletinboard.ad;

import java.time.Instant;

/**
 * An ad as returned by {@code /api/v1/ads} (contract C1): the poster's display name, never an email
 * (FR5.1, FR5.2), and whether the caller owns it (FR4.6).
 */
public record AdView(
        Long id,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        String authorName,
        boolean ownedByMe
) {
}
