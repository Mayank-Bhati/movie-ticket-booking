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
public class Theater {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 500)
    private String address;

    protected Theater() { }
    public Theater(City city, String name, String address) {
        this.city = city; this.name = name; this.address = address;
    }
    public Long getId() { return id; }
    public City getCity() { return city; }
    public String getName() { return name; }
}
