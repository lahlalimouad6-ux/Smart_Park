package com.smartpark.repository;

import com.smartpark.models.ParkingSpotRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRegionRepository extends JpaRepository<ParkingSpotRegion, Long> {
    Optional<ParkingSpotRegion> findBySpotId(Long spotId);
    List<ParkingSpotRegion> findBySpotZoneParkingId(Long parkingId);
}

