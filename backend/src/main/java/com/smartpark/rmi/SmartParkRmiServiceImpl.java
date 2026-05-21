package com.smartpark.rmi;

import com.smartpark.models.Parking;
import com.smartpark.models.Reservation;
import com.smartpark.models.User;
import com.smartpark.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;

@Service
public class SmartParkRmiServiceImpl implements SmartParkRmiService {

    private final ReservationRepository reservationRepository;

    public SmartParkRmiServiceImpl(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public String ping() throws RemoteException {
        return "pong";
    }

    @Override
    public ReservationAccessInfo getReservationAccessInfo(String qrToken) throws RemoteException {
        if (qrToken == null || qrToken.trim().isEmpty()) {
            return null;
        }
        Reservation reservation = reservationRepository.findByQrCodeTokenWithDetails(qrToken.trim()).orElse(null);
        if (reservation == null) {
            return null;
        }

        User user = reservation.getUser();
        Parking parking = (reservation.getSpot() != null
                && reservation.getSpot().getZone() != null
                && reservation.getSpot().getZone().getParking() != null)
                ? reservation.getSpot().getZone().getParking()
                : null;

        return new ReservationAccessInfo(
                reservation.getId(),
                user != null ? user.getEmail() : null,
                user != null ? user.getNom() : null,
                user != null ? user.getPrenom() : null,
                parking != null ? parking.getId() : null,
                parking != null ? parking.getNom() : null,
                parking != null ? parking.getAdresse() : null,
                parking != null ? parking.getVille() : null,
                reservation.getSpot() != null ? reservation.getSpot().getId() : null,
                reservation.getSpot() != null ? reservation.getSpot().getNumeroPlace() : null,
                reservation.getDateDebut(),
                reservation.getDateFin(),
                reservation.getMontantTotal(),
                reservation.getStatut() != null ? reservation.getStatut().name() : null
        );
    }
}

