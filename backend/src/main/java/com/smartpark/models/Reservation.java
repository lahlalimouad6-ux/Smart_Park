package com.smartpark.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "spot_id")
    private ParkingSpot spot;

    @NotNull
    private LocalDateTime dateDebut;

    @NotNull
    private LocalDateTime dateFin;

    @NotNull
    private BigDecimal montantTotal;

    @Column(name = "hourly_rate_applied", precision = 10, scale = 2)
    private BigDecimal hourlyRateApplied;

    @Enumerated(EnumType.STRING)
    private ReservationStatus statut;

    private String qrCodeToken;

    @Column(name = "pricing_breakdown", columnDefinition = "TEXT")
    private String pricingBreakdown;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Reservation() {}

    public enum ReservationStatus {
        EN_ATTENTE, PAYE, ANNULE, TERMINE
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public ParkingSpot getSpot() { return spot; }
    public void setSpot(ParkingSpot spot) { this.spot = spot; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public BigDecimal getMontantTotal() { return montantTotal; }
    public void setMontantTotal(BigDecimal montantTotal) { this.montantTotal = montantTotal; }

    public BigDecimal getHourlyRateApplied() { return hourlyRateApplied; }
    public void setHourlyRateApplied(BigDecimal hourlyRateApplied) { this.hourlyRateApplied = hourlyRateApplied; }

    public ReservationStatus getStatut() { return statut; }
    public void setStatut(ReservationStatus statut) { this.statut = statut; }

    public String getQrCodeToken() { return qrCodeToken; }
    public void setQrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; }

    public String getPricingBreakdown() { return pricingBreakdown; }
    public void setPricingBreakdown(String pricingBreakdown) { this.pricingBreakdown = pricingBreakdown; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
