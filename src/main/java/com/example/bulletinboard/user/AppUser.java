package com.example.bulletinboard.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "users")
public class AppUser {

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = USERNAME_MAX_LENGTH)
    private String username;

    /** Lower-cased username; the unique key that makes usernames case-insensitively unique. */
    @Column(nullable = false, unique = true, length = USERNAME_MAX_LENGTH)
    private String usernameLower;

    @Column(nullable = false)
    private String passwordHash;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.usernameLower = normalize(username);
        this.passwordHash = passwordHash;
    }

    public static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
