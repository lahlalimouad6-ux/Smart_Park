package com.smartpark.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "parking_spots")
public class ParkingSpot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String numeroPlace;

    @Enumerated(EnumType.STRING)
    private SpotType type;

    @Column(name = "coordx")
    private int coordX;

    @Column(name = "coordy")
    private int coordY;

    @Enumerated(EnumType.STRING)
    private SpotStatus statut;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    @JsonIgnore
    private Zone zone;

    public ParkingSpot() {}

    public enum SpotType {
        STANDARD, HANDICAPE, ELECTRIQUE
    }

    public enum SpotStatus {
        LIBRE, OCCUPE, RESERVE
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroPlace() { return numeroPlace; }
    public void setNumeroPlace(String numeroPlace) { this.numeroPlace = numeroPlace; }

    public SpotType getType() { return type; }
    public void setType(SpotType type) { this.type = type; }

    public int getCoordX() { return coordX; }
    public void setCoordX(int coordX) { this.coordX = coordX; }

    public int getCoordY() { return coordY; }
    public void setCoordY(int coordY) { this.coordY = coordY; }

    public SpotStatus getStatut() { return statut; }
    public void setStatut(SpotStatus statut) { this.statut = statut; }

    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }
}
