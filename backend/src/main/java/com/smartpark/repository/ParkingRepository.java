package com.smartpark.repository;

import com.smartpark.models.Parking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParkingRepository extends JpaRepository<Parking, Long> {
    Optional<Parking> findByNom(String nom);

    Page<Parking> findByNomContainingIgnoreCaseOrAdresseContainingIgnoreCaseOrVilleContainingIgnoreCase(
            String nom,
            String adresse,
            String ville,
            Pageable pageable
    );
}
