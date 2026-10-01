package com.example.bulletinboard.ad;

import java.time.Instant;

public record AdResponse(Long id, String title, String description, Instant createdAt, Instant updatedAt) {

    static AdResponse from(Ad ad) {
        return new AdResponse(ad.getId(), ad.getTitle(), ad.getDescription(), ad.getCreatedAt(), ad.getUpdatedAt());
    }
}
