package com.mayankbhati.movietickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Screen {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id", nullable = false)
    private Theater theater;
    @Column(nullable = false, length = 80)
    private String name;

    protected Screen() { }
    public Screen(Theater theater, String name) { this.theater = theater; this.name = name; }
    public Long getId() { return id; }
    public Theater getTheater() { return theater; }
    public String getName() { return name; }
}
