package com.smartpark.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Entity
@Table(name = "zones")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nomZone;

    @ManyToOne
    @JoinColumn(name = "parking_id")
    @JsonIgnore
    private Parking parking;

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL)
    private List<ParkingSpot> spots;

    public Zone() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomZone() { return nomZone; }
    public void setNomZone(String nomZone) { this.nomZone = nomZone; }

    public Parking getParking() { return parking; }
    public void setParking(Parking parking) { this.parking = parking; }

    public List<ParkingSpot> getSpots() { return spots; }
    public void setSpots(List<ParkingSpot> spots) { this.spots = spots; }
}
