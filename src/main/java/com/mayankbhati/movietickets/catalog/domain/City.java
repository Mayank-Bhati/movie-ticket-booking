package com.mayankbhati.movietickets.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class City {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(nullable = false, length = 60)
    private String timezone;

    protected City() { }
    public City(String name, String timezone) { this.name = name; this.timezone = timezone; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
}
