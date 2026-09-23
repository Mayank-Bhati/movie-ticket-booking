package com.mayankbhati.movietickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(length = 20)
    private String certificate;
    @Column(nullable = false, length = 60)
    private String language;

    protected Movie() { }
    public Movie(String title, int durationMinutes, String certificate, String language) {
        this.title = title; this.durationMinutes = durationMinutes;
        this.certificate = certificate; this.language = language;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getCertificate() { return certificate; }
    public String getLanguage() { return language; }
}
