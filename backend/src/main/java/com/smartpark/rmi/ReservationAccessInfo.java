package com.smartpark.rmi;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationAccessInfo(
        Long reservationId,
        String userEmail,
        String userNom,
        String userPrenom,
        Long parkingId,
        String parkingNom,
        String parkingAdresse,
        String parkingVille,
        Long spotId,
        String spotNumeroPlace,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        BigDecimal montantTotal,
        String statut
) implements Serializable {}

