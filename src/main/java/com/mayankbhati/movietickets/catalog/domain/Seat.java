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
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;
    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;
    @Column(name = "seat_number", nullable = false)
    private int seatNumber;
    @Column(nullable = false, length = 20)
    private String category;

    protected Seat() { }
    public Seat(Screen screen, String rowLabel, int seatNumber, String category) {
        this.screen = screen; this.rowLabel = rowLabel; this.seatNumber = seatNumber; this.category = category;
    }
    public Long getId() { return id; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public String getCategory() { return category; }
    public String label() { return rowLabel + seatNumber; }
}
