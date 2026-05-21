package com.smartpark.messaging;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "smartpark.messaging.enabled", havingValue = "true")
public class ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ReservationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Retry(name = "rabbitPublisher")
    @CircuitBreaker(name = "rabbitPublisher", fallbackMethod = "publishFallback")
    public void publish(String type, Long reservationId, Long spotId, LocalDateTime dateDebut, LocalDateTime dateFin) {
        ReservationEvent event = new ReservationEvent(type, reservationId, spotId, dateDebut, dateFin, LocalDateTime.now());
        rabbitTemplate.convertAndSend(RabbitMessagingConfig.EXCHANGE, RabbitMessagingConfig.ROUTING_KEY, event);
    }

    public void publishFallback(String type, Long reservationId, Long spotId, LocalDateTime dateDebut, LocalDateTime dateFin, Throwable t) {
        log.warn("Rabbit publish failed type={} reservationId={} spotId={} err={}", type, reservationId, spotId, t != null ? t.getMessage() : null);
    }
}
