package com.example.bulletinboard.ad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ads")
public class Ad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = AdRequest.TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = AdRequest.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Account id of the poster, set once at creation from the logged-in caller (FR4.2, ADR-005).
     * {@code null} for an unowned ad, which nobody can edit (FR4.5).
     */
    @Column(name = "owner_id", updatable = false)
    private Long ownerId;

    protected Ad() {
    }

    /**
     * Creates an unowned ad.
     */
    public Ad(String title, String description) {
        this(title, description, null);
    }

    public Ad(String title, String description, Long ownerId) {
        this.title = title;
        this.description = description;
        this.ownerId = ownerId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getOwnerId() {
        return ownerId;
    }
}
