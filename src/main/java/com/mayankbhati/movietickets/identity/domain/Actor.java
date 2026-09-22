package com.mayankbhati.movietickets.identity.domain;

public record Actor(long id, String email, Role role) {
    public enum Role {
        ADMIN, CUSTOMER
    }
}

