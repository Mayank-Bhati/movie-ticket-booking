package com.mayankbhati.movietickets.identity.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor.Role role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, Actor.Role role, OffsetDateTime createdAt) {
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Actor.Role getRole() { return role; }

    public Actor toActor() {
        return new Actor(id, email, role);
    }
}
