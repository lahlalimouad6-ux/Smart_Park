package com.smartpark.models;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_spot_regions")
public class ParkingSpotRegion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "spot_id", unique = true)
    private ParkingSpot spot;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double w;

    @Column(nullable = false)
    private double h;

    public ParkingSpotRegion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ParkingSpot getSpot() { return spot; }
    public void setSpot(ParkingSpot spot) { this.spot = spot; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getW() { return w; }
    public void setW(double w) { this.w = w; }

    public double getH() { return h; }
    public void setH(double h) { this.h = h; }
}

