package com.smartpark.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.models.Parking;
import com.smartpark.models.ParkingPricingRule;
import com.smartpark.models.ParkingSpot;
import com.smartpark.models.ParkingSpotRegion;
import com.smartpark.models.ParkingCamera;
import com.smartpark.models.Reservation;
import com.smartpark.models.Zone;
import com.smartpark.repository.ParkingCameraRepository;
import com.smartpark.repository.ParkingPricingRuleRepository;
import com.smartpark.repository.ParkingRepository;
import com.smartpark.repository.ParkingSpotRegionRepository;
import com.smartpark.repository.ParkingSpotRepository;
import com.smartpark.repository.ReservationRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.ZoneRepository;
import com.smartpark.services.CameraOccupancyMonitor;
import com.smartpark.services.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/parkings")
public class ParkingController {

    @Autowired
    private ParkingRepository parkingRepository;

    @Autowired
    private ParkingCameraRepository parkingCameraRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private ParkingSpotRegionRepository regionRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CameraOccupancyMonitor cameraOccupancyMonitor;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private ParkingPricingRuleRepository pricingRuleRepository;

    public record CameraPlanSpotDTO(Long spotId, String numeroPlace, ParkingSpot.SpotStatus statut, Double x, Double y, Double w, Double h) {}
    public record CameraPlanDTO(Long parkingId, String parkingNom, String videoFile, List<CameraPlanSpotDTO> spots) {}
    public record PricingQuoteDTO(
            Long parkingId,
            Long spotId,
            BigDecimal baseHourlyRate,
            BigDecimal totalAmount,
            int totalMinutes,
            BigDecimal minHourlyRate,
            BigDecimal maxHourlyRate,
            List<PricingService.AppliedRule> appliedRules
    ) {}

    public record PricingRuleRequest(
            String name,
            ParkingPricingRule.RuleType ruleType,
            Integer priority,
            Boolean enabled,
            BigDecimal multiplier,
            BigDecimal overrideRate,
            String daysOfWeek,
            String startTime,
            String endTime,
            String startDateTime,
            String endDateTime,
            Double minOccupancyPct,
            Double maxOccupancyPct
    ) {}

    public record PagedResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {}

    @GetMapping
    public List<Parking> getAllParkings() {
        return parkingRepository.findAll();
    }

    @GetMapping("/paged")
    public ResponseEntity<PagedResponse<Parking>> getParkingsPaged(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Parking> result;
        if (StringUtils.hasText(q)) {
            String needle = q.trim();
            result = parkingRepository.findByNomContainingIgnoreCaseOrAdresseContainingIgnoreCaseOrVilleContainingIgnoreCase(
                    needle, needle, needle, pageable
            );
        } else {
            result = parkingRepository.findAll(pageable);
        }

        return ResponseEntity.ok(new PagedResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        ));
    }

    @GetMapping("/with-camera")
    public List<Parking> getParkingsWithCamera() {
        return parkingCameraRepository.findAllParkingsWithCamera();
    }

    @GetMapping("/{id}/camera-plan")
    public ResponseEntity<?> getCameraPlan(@PathVariable Long id) {
        ParkingCamera cam = parkingCameraRepository.findByParkingId(id).orElse(null);
        if (cam == null || cam.getParking() == null) {
            return ResponseEntity.status(404).body("Aucune caméra pour ce parking");
        }

        cameraOccupancyMonitor.ensureLayoutForParking(id);

        List<ParkingSpot> spots = spotRepository.findByZoneParkingId(id);
        Map<Long, ParkingSpotRegion> bySpotId = new HashMap<>();
        for (ParkingSpotRegion r : regionRepository.findBySpotZoneParkingId(id)) {
            if (r.getSpot() != null && r.getSpot().getId() != null) {
                bySpotId.put(r.getSpot().getId(), r);
            }
        }

        List<CameraPlanSpotDTO> dtos = new ArrayList<>(spots.size());
        for (ParkingSpot s : spots) {
            ParkingSpotRegion r = bySpotId.get(s.getId());
            dtos.add(new CameraPlanSpotDTO(
                    s.getId(),
                    s.getNumeroPlace(),
                    s.getStatut(),
                    r != null ? r.getX() : null,
                    r != null ? r.getY() : null,
                    r != null ? r.getW() : null,
                    r != null ? r.getH() : null
            ));
        }

        return ResponseEntity.ok(new CameraPlanDTO(id, cam.getParking().getNom(), cam.getVideoFile(), dtos));
    }

    @GetMapping("/{id}")
    public Parking getParkingById(@PathVariable Long id) {
        return parkingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parking non trouvé"));
    }

    @GetMapping("/{id}/zones")
    public List<Zone> getZonesByParking(@PathVariable Long id) {
        return zoneRepository.findByParkingIdWithSpots(id);
    }

    @GetMapping("/zones/{zoneId}/spots")
    public List<ParkingSpot> getSpotsByZone(@PathVariable Long zoneId) {
        return spotRepository.findByZoneId(zoneId);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    @Transactional
    public Parking createParking(@RequestBody Map<String, Object> payload) {
        Map<String, Object> parkingPayload = new HashMap<>(payload);
        parkingPayload.remove("nombrePlaces");
        Parking parking = objectMapper.convertValue(parkingPayload, Parking.class);
        Parking savedParking = parkingRepository.save(parking);

        Integer nombrePlaces = null;
        Object nombrePlacesObj = payload.get("nombrePlaces");
        if (nombrePlacesObj instanceof Number n) {
            nombrePlaces = n.intValue();
        } else if (nombrePlacesObj instanceof String s) {
            try {
                nombrePlaces = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }

        if (nombrePlaces != null && nombrePlaces > 0) {
            Zone zone = new Zone();
            zone.setNomZone("Zone A");
            zone.setParking(savedParking);
            Zone savedZone = zoneRepository.save(zone);

            int cols = 8;
            int spacing = 60;
            List<ParkingSpot> spots = new ArrayList<>();
            for (int i = 1; i <= nombrePlaces; i++) {
                ParkingSpot spot = new ParkingSpot();
                spot.setNumeroPlace(String.format("A%02d", i));
                spot.setType(ParkingSpot.SpotType.STANDARD);
                spot.setStatut(ParkingSpot.SpotStatus.LIBRE);
                spot.setCoordX(((i - 1) % cols) * spacing);
                spot.setCoordY(((i - 1) / cols) * spacing);
                spot.setZone(savedZone);
                spots.add(spot);
            }
            spotRepository.saveAll(spots);
        }

        return savedParking;
    }

    @PostMapping("/{parkingId}/zones")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public Zone createZone(@PathVariable Long parkingId, @RequestBody Zone zone) {
        Parking parking = parkingRepository.findById(parkingId)
                .orElseThrow(() -> new RuntimeException("Parking non trouvé"));
        zone.setParking(parking);
        return zoneRepository.save(zone);
    }

    @PostMapping("/zones/{zoneId}/spots")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ParkingSpot createSpot(@PathVariable Long zoneId, @RequestBody ParkingSpot spot) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new RuntimeException("Zone non trouvée"));
        spot.setZone(zone);
        return spotRepository.save(spot);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public Parking updateParking(@PathVariable Long id, @RequestBody Parking parkingDetails) {
        Parking parking = parkingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parking non trouvé"));
        parking.setNom(parkingDetails.getNom());
        parking.setAdresse(parkingDetails.getAdresse());
        parking.setVille(parkingDetails.getVille());
        parking.setCoordGps(parkingDetails.getCoordGps());
        parking.setTarifHeure(parkingDetails.getTarifHeure());
        return parkingRepository.save(parking);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public Zone updateZone(@PathVariable Long id, @RequestBody Zone zoneDetails) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone non trouvée"));
        zone.setNomZone(zoneDetails.getNomZone());
        return zoneRepository.save(zone);
    }

    @PutMapping("/spots/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ParkingSpot updateSpot(@PathVariable Long id, @RequestBody ParkingSpot spotDetails) {
        ParkingSpot spot = spotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Place non trouvée"));
        spot.setNumeroPlace(spotDetails.getNumeroPlace());
        spot.setType(spotDetails.getType());
        spot.setCoordX(spotDetails.getCoordX());
        spot.setCoordY(spotDetails.getCoordY());
        spot.setStatut(spotDetails.getStatut());
        return spotRepository.save(spot);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public void deleteParking(@PathVariable Long id) {
        parkingRepository.deleteById(id);
    }

    @DeleteMapping("/zones/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public void deleteZone(@PathVariable Long id) {
        zoneRepository.deleteById(id);
    }

    @DeleteMapping("/spots/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public void deleteSpot(@PathVariable Long id) {
        spotRepository.deleteById(id);
    }

    @GetMapping("/{parkingId}/pricing/quote")
    public ResponseEntity<?> quotePricing(
            @PathVariable Long parkingId,
            @RequestParam Long spotId,
            @RequestParam String dateDebut,
            @RequestParam String dateFin
    ) {
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(dateDebut);
            end = LocalDateTime.parse(dateFin);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Dates invalides");
        }

        try {
            PricingService.Quote quote = pricingService.quoteForSpot(spotId, start, end);
            if (quote.parkingId() == null || !quote.parkingId().equals(parkingId)) {
                return ResponseEntity.badRequest().body("Place non associée à ce parking");
            }
            return ResponseEntity.ok(new PricingQuoteDTO(
                    quote.parkingId(),
                    quote.spotId(),
                    quote.baseHourlyRate(),
                    quote.totalAmount(),
                    quote.totalMinutes(),
                    quote.minHourlyRate(),
                    quote.maxHourlyRate(),
                    quote.appliedRules()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{parkingId}/pricing-rules")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public List<ParkingPricingRule> listPricingRules(@PathVariable Long parkingId) {
        return pricingRuleRepository.findByParkingIdOrderByPriorityAscIdAsc(parkingId);
    }

    @PostMapping("/{parkingId}/pricing-rules")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> createPricingRule(@PathVariable Long parkingId, @RequestBody PricingRuleRequest req) {
        Parking parking = parkingRepository.findById(parkingId).orElse(null);
        if (parking == null) {
            return ResponseEntity.status(404).body("Parking non trouvé");
        }
        if (req == null || req.ruleType() == null) {
            return ResponseEntity.badRequest().body("ruleType requis");
        }

        ParkingPricingRule rule = new ParkingPricingRule();
        rule.setParking(parking);
        rule.setRuleType(req.ruleType());
        rule.setName(req.name() != null && !req.name().isBlank() ? req.name().trim() : req.ruleType().name());
        rule.setEnabled(req.enabled() == null || req.enabled());
        rule.setPriority(req.priority() != null ? req.priority() : 100);
        rule.setMultiplier(req.multiplier());
        rule.setOverrideRate(req.overrideRate());
        rule.setDaysOfWeek(req.daysOfWeek());
        rule.setMinOccupancyPct(req.minOccupancyPct());
        rule.setMaxOccupancyPct(req.maxOccupancyPct());

        try {
            if (req.startTime() != null && !req.startTime().isBlank()) {
                rule.setStartTime(LocalTime.parse(req.startTime().trim()));
            }
            if (req.endTime() != null && !req.endTime().isBlank()) {
                rule.setEndTime(LocalTime.parse(req.endTime().trim()));
            }
            if (req.startDateTime() != null && !req.startDateTime().isBlank()) {
                rule.setStartDateTime(LocalDateTime.parse(req.startDateTime().trim()));
            }
            if (req.endDateTime() != null && !req.endDateTime().isBlank()) {
                rule.setEndDateTime(LocalDateTime.parse(req.endDateTime().trim()));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Format de date/heure invalide");
        }

        ParkingPricingRule saved = pricingRuleRepository.save(rule);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/pricing-rules/{ruleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> deletePricingRule(@PathVariable Long ruleId) {
        if (ruleId == null) {
            return ResponseEntity.badRequest().body("ruleId invalide");
        }
        if (!pricingRuleRepository.existsById(ruleId)) {
            return ResponseEntity.notFound().build();
        }
        pricingRuleRepository.deleteById(ruleId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public Map<String, Object> getAdminStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalParkings = parkingRepository.count();
        long totalUsers = userRepository.count();
        long totalSpots = spotRepository.count();
        long occupiedSpots = spotRepository.countByStatut(ParkingSpot.SpotStatus.OCCUPE);
        long activeReservations = reservationRepository.countByStatut(Reservation.ReservationStatus.PAYE);

        BigDecimal revenue = reservationRepository.sumMontantTotalByStatutIn(
                List.of(Reservation.ReservationStatus.PAYE, Reservation.ReservationStatus.TERMINE)
        );

        double occupationRate = totalSpots > 0 ? (double) occupiedSpots / (double) totalSpots * 100.0 : 0.0;

        stats.put("totalParkings", totalParkings);
        stats.put("totalUsers", totalUsers);
        stats.put("totalSpots", totalSpots);
        stats.put("occupiedSpots", occupiedSpots);
        stats.put("activeReservations", activeReservations);
        stats.put("totalRevenue", revenue);
        stats.put("occupationRate", occupationRate);

        return stats;
    }
}
