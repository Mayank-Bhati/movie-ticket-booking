package com.moviebooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;

    @Column(nullable = false, length = 5)
    private String rowLabel;

    @Column(nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatType seatType;

    public String label() {
        return rowLabel + seatNumber;
    }

    public enum SeatType { REGULAR, PREMIUM }
}
