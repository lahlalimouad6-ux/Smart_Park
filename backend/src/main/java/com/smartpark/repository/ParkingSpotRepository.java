package com.smartpark.repository;

import com.smartpark.models.ParkingSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
    List<ParkingSpot> findByZoneId(Long zoneId);
    long countByStatut(ParkingSpot.SpotStatus statut);
    List<ParkingSpot> findByZoneParkingId(Long parkingId);
    Optional<ParkingSpot> findByNumeroPlaceAndZoneParkingId(String numeroPlace, Long parkingId);
    long countByZoneParkingId(Long parkingId);
    long countByZoneParkingIdAndStatut(Long parkingId, ParkingSpot.SpotStatus statut);
}
