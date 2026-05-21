package com.smartpark.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ReservationEvent(
        String type,
        Long reservationId,
        Long spotId,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        LocalDateTime emittedAt
) implements Serializable {}

