package com.example.bulletinboard.auth;

import com.example.bulletinboard.user.AppUser;

public record UserResponse(Long id, String username) {

    static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
