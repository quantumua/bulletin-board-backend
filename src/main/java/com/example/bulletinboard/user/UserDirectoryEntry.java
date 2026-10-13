package com.example.bulletinboard.user;

import java.time.Instant;

/** One listed user in the directory; deliberately separate from the entity so no other field can leak. */
public record UserDirectoryEntry(Long id, String username, Instant createdAt) {

    static UserDirectoryEntry from(AppUser user) {
        return new UserDirectoryEntry(user.getId(), user.getUsername(), user.getCreatedAt());
    }
}
