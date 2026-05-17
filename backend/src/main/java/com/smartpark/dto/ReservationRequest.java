package com.smartpark.dto;

import java.time.LocalDateTime;

public class ReservationRequest {
    private Long spotId;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private boolean useSubscription;

    public ReservationRequest() {}

    // Getters and Setters
    public Long getSpotId() { return spotId; }
    public void setSpotId(Long spotId) { this.spotId = spotId; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public boolean isUseSubscription() { return useSubscription; }
    public void setUseSubscription(boolean useSubscription) { this.useSubscription = useSubscription; }
}
