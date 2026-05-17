package com.smartpark.repository;

import com.smartpark.models.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByParkingId(Long parkingId);

    @Query("SELECT DISTINCT z FROM Zone z LEFT JOIN FETCH z.spots WHERE z.parking.id = :parkingId")
    List<Zone> findByParkingIdWithSpots(@Param("parkingId") Long parkingId);
}
