package com.smartpark.ws;

import com.smartpark.models.Parking;
import com.smartpark.models.ParkingSpot;
import com.smartpark.models.Reservation;
import com.smartpark.repository.ParkingRepository;
import com.smartpark.repository.ParkingSpotRepository;
import com.smartpark.repository.ReservationRepository;
import com.smartpark.ws.schema.GetParkingPlanRequest;
import com.smartpark.ws.schema.GetParkingPlanResponse;
import com.smartpark.ws.schema.SpotEntry;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Endpoint
public class ParkingSoapEndpoint {

    private static final String NAMESPACE_URI = "http://smartpark.com/ws";

    private final ParkingRepository parkingRepository;
    private final ParkingSpotRepository spotRepository;
    private final ReservationRepository reservationRepository;

    public ParkingSoapEndpoint(ParkingRepository parkingRepository, ParkingSpotRepository spotRepository, ReservationRepository reservationRepository) {
        this.parkingRepository = parkingRepository;
        this.spotRepository = spotRepository;
        this.reservationRepository = reservationRepository;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "GetParkingPlanRequest")
    @ResponsePayload
    public GetParkingPlanResponse getParkingPlan(@RequestPayload GetParkingPlanRequest request) {
        long parkingId = request.getParkingId();
        Parking parking = parkingRepository.findById(parkingId).orElse(null);

        List<ParkingSpot> spots = spotRepository.findByZoneParkingId(parkingId);
        List<Long> spotIds = new ArrayList<>(spots.size());
        for (ParkingSpot s : spots) {
            if (s != null && s.getId() != null) {
                spotIds.add(s.getId());
            }
        }

        Map<Long, LocalDateTime> nextStartBySpotId = new HashMap<>();
        if (!spotIds.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Reservation.ReservationStatus> activeStatuses = List.of(
                    Reservation.ReservationStatus.PAYE,
                    Reservation.ReservationStatus.EN_ATTENTE
            );
            for (Object[] r : reservationRepository.findNextReservationStarts(spotIds, now, activeStatuses)) {
                if (r == null || r.length < 2) continue;
                Long sid = (Long) r[0];
                LocalDateTime next = (LocalDateTime) r[1];
                if (sid != null) nextStartBySpotId.put(sid, next);
            }
        }

        GetParkingPlanResponse res = new GetParkingPlanResponse();
        res.setParkingId(parking != null ? parking.getId() : parkingId);
        res.setParkingNom(parking != null ? parking.getNom() : null);

        for (ParkingSpot s : spots) {
            if (s == null || s.getId() == null) continue;
            SpotEntry e = new SpotEntry();
            e.setSpotId(s.getId());
            e.setNumeroPlace(s.getNumeroPlace());
            e.setStatut(s.getStatut() != null ? s.getStatut().name() : "");
            LocalDateTime next = nextStartBySpotId.get(s.getId());
            if (next != null) {
                e.setNextReservationStart(toXml(next));
            }
            res.getSpots().add(e);
        }

        return res;
    }

    private static XMLGregorianCalendar toXml(LocalDateTime dt) {
        try {
            GregorianCalendar cal = GregorianCalendar.from(dt.atZone(ZoneId.systemDefault()));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        } catch (Exception e) {
            return null;
        }
    }
}

