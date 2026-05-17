package com.smartpark.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "parkings")
public class Parking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String nom;

    @NotBlank
    private String adresse;

    @NotBlank
    private String ville;

    private String coordGps;

    @NotNull
    private BigDecimal tarifHeure;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "parking", cascade = CascadeType.ALL)
    private List<Zone> zones;

    public Parking() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public String getCoordGps() { return coordGps; }
    public void setCoordGps(String coordGps) { this.coordGps = coordGps; }

    public BigDecimal getTarifHeure() { return tarifHeure; }
    public void setTarifHeure(BigDecimal tarifHeure) { this.tarifHeure = tarifHeure; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Zone> getZones() { return zones; }
    public void setZones(List<Zone> zones) { this.zones = zones; }
}
