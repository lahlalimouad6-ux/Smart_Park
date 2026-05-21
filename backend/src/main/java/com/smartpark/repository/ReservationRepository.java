package com.smartpark.repository;

import com.smartpark.models.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserId(Long userId);
    Optional<Reservation> findByQrCodeToken(String qrCodeToken);

    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.user u
        JOIN FETCH r.spot s
        JOIN FETCH s.zone z
        JOIN FETCH z.parking p
        WHERE r.qrCodeToken = :token
        """)
    Optional<Reservation> findByQrCodeTokenWithDetails(@Param("token") String token);

    @Query(
            value = """
        SELECT r.id, u.id, u.nom, u.prenom, u.email,
               p.id, p.nom,
               s.id, s.numeroPlace,
               r.dateDebut, r.dateFin, r.montantTotal, r.statut
        FROM Reservation r
        JOIN r.user u
        JOIN r.spot s
        JOIN s.zone z
        JOIN z.parking p
        WHERE (:q IS NULL OR :q = '' OR
               LOWER(u.email) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(u.nom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(u.prenom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(p.nom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(s.numeroPlace) LIKE CONCAT('%', LOWER(:q), '%'))
          AND (:reservationId IS NULL OR r.id = :reservationId)
        ORDER BY r.createdAt DESC
        """,
            countQuery = """
        SELECT COUNT(r)
        FROM Reservation r
        JOIN r.user u
        JOIN r.spot s
        JOIN s.zone z
        JOIN z.parking p
        WHERE (:q IS NULL OR :q = '' OR
               LOWER(u.email) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(u.nom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(u.prenom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(p.nom) LIKE CONCAT('%', LOWER(:q), '%') OR
               LOWER(s.numeroPlace) LIKE CONCAT('%', LOWER(:q), '%'))
          AND (:reservationId IS NULL OR r.id = :reservationId)
        """
    )
    Page<Object[]> findAdminReservationOverviewPage(
            @Param("q") String q,
            @Param("reservationId") Long reservationId,
            Pageable pageable
    );

    @Query("""
        SELECT r.id, u.id, u.nom, u.prenom, u.email,
               p.id, p.nom,
               s.id, s.numeroPlace,
               r.dateDebut, r.dateFin, r.montantTotal, r.statut
        FROM Reservation r
        JOIN r.user u
        JOIN r.spot s
        JOIN s.zone z
        JOIN z.parking p
        ORDER BY r.createdAt DESC
        """)
    List<Object[]> findAdminReservationOverview();

    @Query("SELECT r FROM Reservation r WHERE r.spot.id = :spotId AND " +
           "((r.dateDebut < :dateFin AND r.dateFin > :dateDebut)) AND " +
           "r.statut IN (:statuses)")
    List<Reservation> findOverlappingReservations(@Param("spotId") Long spotId, 
                                                @Param("dateDebut") LocalDateTime dateDebut, 
                                                @Param("dateFin") LocalDateTime dateFin,
                                                @Param("statuses") List<Reservation.ReservationStatus> statuses);

    @Query("""
        SELECT r.spot.id, MIN(r.dateDebut)
        FROM Reservation r
        WHERE r.spot.id IN :spotIds
          AND r.dateDebut > :now
          AND r.statut IN :statuses
        GROUP BY r.spot.id
        """)
    List<Object[]> findNextReservationStarts(
            @Param("spotIds") List<Long> spotIds,
            @Param("now") LocalDateTime now,
            @Param("statuses") List<Reservation.ReservationStatus> statuses
    );

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.spot.id = :spotId
          AND r.dateDebut <= :now
          AND r.dateFin > :now
          AND r.statut IN :statuses
        """)
    List<Reservation> findActiveReservationsAt(
            @Param("spotId") Long spotId,
            @Param("now") LocalDateTime now,
            @Param("statuses") List<Reservation.ReservationStatus> statuses
    );

    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.spot s
        WHERE r.dateDebut <= :now
          AND r.dateFin > :now
          AND r.statut IN :statuses
        """)
    List<Reservation> findActiveReservationsForActivation(
            @Param("now") LocalDateTime now,
            @Param("statuses") List<Reservation.ReservationStatus> statuses
    );

    @Query("SELECT COALESCE(SUM(r.montantTotal), 0) FROM Reservation r WHERE r.statut IN (:statuses)")
    BigDecimal sumMontantTotalByStatutIn(@Param("statuses") List<Reservation.ReservationStatus> statuses);

    long countByStatut(Reservation.ReservationStatus statut);

    List<Reservation> findByStatutAndDateFinBefore(Reservation.ReservationStatus statut, LocalDateTime dateFin);
}
