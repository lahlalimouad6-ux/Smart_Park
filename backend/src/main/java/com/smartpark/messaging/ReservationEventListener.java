package com.smartpark.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "smartpark.messaging.enabled", havingValue = "true")
public class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);

    @RabbitListener(queues = RabbitMessagingConfig.QUEUE)
    public void onReservationEvent(ReservationEvent event) {
        if (event == null) return;
        log.info("ReservationEvent type={} reservationId={} spotId={} dateDebut={} dateFin={} emittedAt={}",
                event.type(), event.reservationId(), event.spotId(), event.dateDebut(), event.dateFin(), event.emittedAt());
    }
}

