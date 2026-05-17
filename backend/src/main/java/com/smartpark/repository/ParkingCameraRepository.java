package com.smartpark.repository;

import com.smartpark.models.ParkingCamera;
import com.smartpark.models.Parking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingCameraRepository extends JpaRepository<ParkingCamera, Long> {
    Optional<ParkingCamera> findByParkingId(Long parkingId);

    @Query("select c.parking from ParkingCamera c")
    List<Parking> findAllParkingsWithCamera();
}
