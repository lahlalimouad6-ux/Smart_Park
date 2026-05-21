package com.smartpark.services;

import com.smartpark.dto.ReservationRequest;
import com.smartpark.messaging.ReservationEventPublisher;
import com.smartpark.models.*;
import com.smartpark.repository.ParkingSpotRepository;
import com.smartpark.repository.ReservationRepository;
import com.smartpark.repository.SubscriptionRepository;
import com.smartpark.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private ObjectProvider<ReservationEventPublisher> reservationEventPublisher;

    /**
     * Vérifie si une place est disponible pour un créneau donné.
     */
    public boolean isSpotAvailable(Long spotId, LocalDateTime debut, LocalDateTime fin) {
        List<Reservation.ReservationStatus> activeStatuses = List.of(
            Reservation.ReservationStatus.PAYE, 
            Reservation.ReservationStatus.EN_ATTENTE
        );
        List<Reservation> overlapping = reservationRepository.findOverlappingReservations(spotId, debut, fin, activeStatuses);
        return overlapping.isEmpty();
    }

    /**
     * Calcule le montant total d'une réservation.
     */
    public BigDecimal calculatePrice(BigDecimal hourlyRate, LocalDateTime debut, LocalDateTime fin) {
        long minutes = Duration.between(debut, fin).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        return hourlyRate.multiply(hours);
    }

    /**
     * Crée une nouvelle réservation.
     */
    @Transactional
    public Reservation createReservation(Long userId, ReservationRequest request) {
        if (request == null || request.getSpotId() == null) {
            throw new RuntimeException("Requête invalide");
        }
        if (request.getDateDebut() == null || request.getDateFin() == null) {
            throw new RuntimeException("Dates invalides");
        }
        if (!request.getDateDebut().isBefore(request.getDateFin())) {
            throw new RuntimeException("La date de fin doit être après la date de début");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        ParkingSpot spot = spotRepository.findById(request.getSpotId())
                .orElseThrow(() -> new RuntimeException("Place non trouvée"));

        if (!isSpotAvailable(request.getSpotId(), request.getDateDebut(), request.getDateFin())) {
            throw new RuntimeException("La place est déjà réservée pour ce créneau");
        }

        BigDecimal montantTotal;
        Reservation.ReservationStatus statutReservation;
        BigDecimal hourlyRateApplied = null;
        String pricingBreakdown = null;
        if (request.isUseSubscription()) {
            List<Subscription> activeSubs = subscriptionRepository.findByUserIdAndActifTrue(userId);
            if (activeSubs.isEmpty()) {
                throw new RuntimeException("Aucun abonnement actif trouvé");
            }
            montantTotal = BigDecimal.ZERO; // Inclus dans l'abonnement
            statutReservation = Reservation.ReservationStatus.PAYE;
        } else {
            PricingService.Quote quote = pricingService.quoteForSpot(request.getSpotId(), request.getDateDebut(), request.getDateFin());
            montantTotal = quote.totalAmount();
            long minutes = Duration.between(request.getDateDebut(), request.getDateFin()).toMinutes();
            if (minutes > 0) {
                BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
                if (hours.compareTo(BigDecimal.ZERO) > 0) {
                    hourlyRateApplied = montantTotal.divide(hours, 2, RoundingMode.HALF_UP);
                }
            }
            if (quote.appliedRules() != null && !quote.appliedRules().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (PricingService.AppliedRule r : quote.appliedRules()) {
                    if (r == null) continue;
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(r.type()).append(":").append(r.name());
                    if (r.overrideRate() != null) {
                        sb.append("(rate=").append(r.overrideRate()).append(")");
                    } else if (r.multiplier() != null) {
                        sb.append("(x").append(r.multiplier()).append(")");
                    }
                }
                pricingBreakdown = sb.toString();
            }
            statutReservation = Reservation.ReservationStatus.PAYE;
        }

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setSpot(spot);
        reservation.setDateDebut(request.getDateDebut());
        reservation.setDateFin(request.getDateFin());
        reservation.setMontantTotal(montantTotal);
        reservation.setHourlyRateApplied(hourlyRateApplied);
        reservation.setPricingBreakdown(pricingBreakdown);
        reservation.setStatut(statutReservation);
        reservation.setQrCodeToken(UUID.randomUUID().toString());

        LocalDateTime now = LocalDateTime.now();
        boolean activeNow =
                (now.isEqual(request.getDateDebut()) || now.isAfter(request.getDateDebut()))
                        && now.isBefore(request.getDateFin());
        if (activeNow) {
            spot.setStatut(ParkingSpot.SpotStatus.OCCUPE);
            spotRepository.save(spot);
        }

        Reservation saved = reservationRepository.save(reservation);
        ReservationEventPublisher publisher = reservationEventPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish("RESERVATION_CREATED", saved.getId(), spot.getId(), saved.getDateDebut(), saved.getDateFin());
        }
        return saved;
    }

    @Transactional
    public Reservation cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));

        if (reservation.getUser() == null || reservation.getUser().getId() == null || !reservation.getUser().getId().equals(userId)) {
            throw new RuntimeException("Accès refusé");
        }

        if (reservation.getStatut() == Reservation.ReservationStatus.ANNULE
                || reservation.getStatut() == Reservation.ReservationStatus.TERMINE) {
            throw new RuntimeException("Réservation non annulable");
        }

        LocalDateTime now = LocalDateTime.now();
        if (reservation.getDateDebut() != null && !now.isBefore(reservation.getDateDebut())) {
            throw new RuntimeException("Impossible d'annuler après le début de la réservation");
        }

        reservation.setStatut(Reservation.ReservationStatus.ANNULE);

        ParkingSpot spot = reservation.getSpot();
        if (spot != null) {
            LocalDateTime now1 = LocalDateTime.now();
            List<Reservation.ReservationStatus> activeStatuses = List.of(
                    Reservation.ReservationStatus.PAYE,
                    Reservation.ReservationStatus.EN_ATTENTE
            );
            boolean hasActiveNow = !reservationRepository.findActiveReservationsAt(spot.getId(), now1, activeStatuses).isEmpty();
            if (!hasActiveNow) {
                spot.setStatut(ParkingSpot.SpotStatus.LIBRE);
                spotRepository.save(spot);
            }
        }

        Reservation saved = reservationRepository.save(reservation);
        ReservationEventPublisher publisher = reservationEventPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish("RESERVATION_CANCELLED", saved.getId(), spot != null ? spot.getId() : null, saved.getDateDebut(), saved.getDateFin());
        }
        return saved;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void activateDueReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation.ReservationStatus> activeStatuses = List.of(
                Reservation.ReservationStatus.PAYE,
                Reservation.ReservationStatus.EN_ATTENTE
        );
        List<Reservation> due = reservationRepository.findActiveReservationsForActivation(now, activeStatuses);
        if (due.isEmpty()) {
            return;
        }

        for (Reservation r : due) {
            ParkingSpot spot = r.getSpot();
            if (spot == null) continue;
            if (spot.getStatut() != ParkingSpot.SpotStatus.OCCUPE) {
                spot.setStatut(ParkingSpot.SpotStatus.OCCUPE);
                spotRepository.save(spot);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expired = reservationRepository.findByStatutAndDateFinBefore(Reservation.ReservationStatus.PAYE, now);
        if (expired.isEmpty()) {
            return;
        }

        for (Reservation r : expired) {
            ParkingSpot spot = r.getSpot();
            if (spot != null) {
                spot.setStatut(ParkingSpot.SpotStatus.LIBRE);
                spotRepository.save(spot);
            }
            r.setStatut(Reservation.ReservationStatus.TERMINE);
            Reservation saved = reservationRepository.save(r);
            ReservationEventPublisher publisher = reservationEventPublisher.getIfAvailable();
            if (publisher != null) {
                publisher.publish("RESERVATION_TERMINATED", saved.getId(), spot != null ? spot.getId() : null, saved.getDateDebut(), saved.getDateFin());
            }
        }
    }
}
