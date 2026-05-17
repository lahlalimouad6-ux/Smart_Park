package com.smartpark.controllers;

import com.smartpark.models.Parking;
import com.smartpark.models.ParkingCamera;
import com.smartpark.models.ParkingSpot;
import com.smartpark.models.ParkingSpotRegion;
import com.smartpark.models.Zone;
import com.smartpark.repository.ParkingCameraRepository;
import com.smartpark.repository.ParkingSpotRegionRepository;
import com.smartpark.repository.ParkingSpotRepository;
import com.smartpark.repository.ZoneRepository;
import com.smartpark.security.jwt.JwtUtils;
import com.smartpark.security.services.UserDetailsServiceImpl;
import com.smartpark.services.CameraOccupancyMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.support.ResourceRegion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/cameras")
public class CameraController {
    private static final long CHUNK_SIZE = 2_000_000;
    private static final long TOKEN_CACHE_TTL_MS = 60_000;
    private static final int TOKEN_CACHE_MAX = 5_000;
    private static final ConcurrentHashMap<String, TokenCacheEntry> TOKEN_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private ParkingCameraRepository parkingCameraRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private ParkingSpotRegionRepository regionRepository;

    @Autowired
    private CameraOccupancyMonitor cameraOccupancyMonitor;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    public record CameraParkingSummary(Long parkingId, String parkingNom, String videoFile, int configuredSpots) {}

    public record CameraSpotDTO(Long spotId, String numeroPlace, ParkingSpot.SpotStatus statut, Double x, Double y, Double w, Double h) {}

    public record CameraParkingDTO(Long parkingId, String parkingNom, String videoFile, List<CameraSpotDTO> spots) {}

    public record RegionInput(String numeroPlace, double x, double y, double w, double h) {}

    public record LayoutRequest(List<RegionInput> spots) {}

    public record StatusInput(Long spotId, ParkingSpot.SpotStatus statut) {}
    public record LiveStatusDTO(Long spotId, ParkingSpot.SpotStatus statut) {}

    private boolean isAdmin(UserDetails userDetails) {
        for (GrantedAuthority a : userDetails.getAuthorities()) {
            if ("ADMIN".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority())) return true;
        }
        return false;
    }

    private boolean validateAdminToken(String token) {
        if (!StringUtils.hasText(token)) return false;
        String trimmed = token.trim();
        long now = System.currentTimeMillis();
        TokenCacheEntry cached = TOKEN_CACHE.get(trimmed);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.isAdmin();
        }

        if (!jwtUtils.validateJwtToken(trimmed)) {
            TOKEN_CACHE.remove(trimmed);
            return false;
        }

        String username = jwtUtils.getUserNameFromJwtToken(trimmed);
        UserDetails details = userDetailsService.loadUserByUsername(username);
        boolean admin = isAdmin(details);
        if (TOKEN_CACHE.size() > TOKEN_CACHE_MAX) {
            TOKEN_CACHE.clear();
        }
        TOKEN_CACHE.put(trimmed, new TokenCacheEntry(admin, now + TOKEN_CACHE_TTL_MS));
        return admin;
    }

    private Path datasetParkingDir() {
        return Path.of(System.getProperty("user.dir")).resolve("..").resolve("datasets").resolve("parking").normalize();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public List<CameraParkingSummary> listCameraParkings() {
        List<ParkingCamera> cameras = parkingCameraRepository.findAll();
        List<CameraParkingSummary> out = new ArrayList<>(cameras.size());
        for (ParkingCamera cam : cameras) {
            Parking p = cam.getParking();
            int count = regionRepository.findBySpotZoneParkingId(p.getId()).size();
            out.add(new CameraParkingSummary(p.getId(), p.getNom(), cam.getVideoFile(), count));
        }
        return out;
    }

    @GetMapping("/{parkingId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getCameraParking(@PathVariable Long parkingId) {
        ParkingCamera cam = parkingCameraRepository.findByParkingId(parkingId).orElse(null);
        if (cam == null) {
            return ResponseEntity.status(404).body("Parking caméra introuvable");
        }

        cameraOccupancyMonitor.ensureLayoutForParking(parkingId);

        List<ParkingSpot> spots = spotRepository.findByZoneParkingId(parkingId);
        Map<Long, ParkingSpotRegion> bySpotId = new HashMap<>();
        for (ParkingSpotRegion r : regionRepository.findBySpotZoneParkingId(parkingId)) {
            if (r.getSpot() != null && r.getSpot().getId() != null) {
                bySpotId.put(r.getSpot().getId(), r);
            }
        }

        List<CameraSpotDTO> dtos = new ArrayList<>(spots.size());
        for (ParkingSpot s : spots) {
            ParkingSpotRegion r = bySpotId.get(s.getId());
            dtos.add(new CameraSpotDTO(
                    s.getId(),
                    s.getNumeroPlace(),
                    s.getStatut(),
                    r != null ? r.getX() : null,
                    r != null ? r.getY() : null,
                    r != null ? r.getW() : null,
                    r != null ? r.getH() : null
            ));
        }
        return ResponseEntity.ok(new CameraParkingDTO(parkingId, cam.getParking().getNom(), cam.getVideoFile(), dtos));
    }

    @PostMapping("/{parkingId}/layout")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    @Transactional
    public ResponseEntity<?> saveLayout(@PathVariable Long parkingId, @RequestBody LayoutRequest request) {
        ParkingCamera cam = parkingCameraRepository.findByParkingId(parkingId).orElse(null);
        if (cam == null) {
            return ResponseEntity.status(404).body("Parking caméra introuvable");
        }

        Parking parking = cam.getParking();
        List<Zone> zones = zoneRepository.findByParkingId(parkingId);
        Zone zone;
        if (zones.isEmpty()) {
            zone = new Zone();
            zone.setNomZone("Zone A");
            zone.setParking(parking);
            zone = zoneRepository.save(zone);
        } else {
            zone = zones.get(0);
        }

        List<RegionInput> inputs = request != null && request.spots() != null ? request.spots() : List.of();
        int index = 0;
        for (RegionInput in : inputs) {
            if (in == null || !StringUtils.hasText(in.numeroPlace())) continue;
            String numero = in.numeroPlace().trim();
            ParkingSpot spot = spotRepository.findByNumeroPlaceAndZoneParkingId(numero, parkingId).orElse(null);
            if (spot == null) {
                ParkingSpot s = new ParkingSpot();
                s.setNumeroPlace(numero);
                s.setType(ParkingSpot.SpotType.STANDARD);
                s.setStatut(ParkingSpot.SpotStatus.LIBRE);
                int cols = 8;
                int spacing = 60;
                int i = index + 1;
                s.setCoordX(((i - 1) % cols) * spacing);
                s.setCoordY(((i - 1) / cols) * spacing);
                s.setZone(zone);
                spot = spotRepository.save(s);
            }

            ParkingSpotRegion region = regionRepository.findBySpotId(spot.getId()).orElse(null);
            if (region == null) {
                region = new ParkingSpotRegion();
                region.setSpot(spot);
            }

            region.setX(Math.max(0, Math.min(1, in.x())));
            region.setY(Math.max(0, Math.min(1, in.y())));
            region.setW(Math.max(0, Math.min(1, in.w())));
            region.setH(Math.max(0, Math.min(1, in.h())));
            regionRepository.save(region);

            index++;
        }

        return getCameraParking(parkingId);
    }

    @PostMapping("/{parkingId}/spots/status")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    @Transactional
    public ResponseEntity<?> updateSpotStatuses(@PathVariable Long parkingId, @RequestBody List<StatusInput> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        for (StatusInput s : statuses) {
            if (s == null || s.spotId() == null || s.statut() == null) continue;
            ParkingSpot spot = spotRepository.findById(s.spotId()).orElse(null);
            if (spot == null || spot.getZone() == null || spot.getZone().getParking() == null) continue;
            if (!parkingId.equals(spot.getZone().getParking().getId())) continue;
            spot.setStatut(s.statut());
            spotRepository.save(spot);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{parkingId}/live-status")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getLiveStatuses(@PathVariable Long parkingId, @RequestParam(name = "second", defaultValue = "0") double second) {
        ParkingCamera cam = parkingCameraRepository.findByParkingId(parkingId).orElse(null);
        if (cam == null || cam.getParking() == null) {
            return ResponseEntity.status(404).body("Parking caméra introuvable");
        }

        List<ParkingSpotRegion> regions = regionRepository.findBySpotZoneParkingId(parkingId);
        if (regions == null || regions.isEmpty()) {
            cameraOccupancyMonitor.ensureLayoutForParking(parkingId);
            regions = regionRepository.findBySpotZoneParkingId(parkingId);
            if (regions == null || regions.isEmpty()) return ResponseEntity.ok(List.of());
        }

        Map<Long, ParkingSpot.SpotStatus> statuses = cameraOccupancyMonitor.predictLiveStatuses(cam, regions, second);
        if (statuses.isEmpty()) return ResponseEntity.ok(List.of());

        List<LiveStatusDTO> out = new ArrayList<>(statuses.size());
        for (Map.Entry<Long, ParkingSpot.SpotStatus> e : statuses.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            out.add(new LiveStatusDTO(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparingLong(LiveStatusDTO::spotId));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/video/{fileName}")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable String fileName,
            @RequestParam(name = "token", required = false) String token,
            @RequestHeader HttpHeaders headers
    ) throws Exception {
        if (!validateAdminToken(token)) {
            return ResponseEntity.status(401).build();
        }

        Path base = datasetParkingDir();
        Path videoPath = base.resolve(fileName).normalize();
        if (!videoPath.startsWith(base) || !Files.exists(videoPath)) {
            return ResponseEntity.status(404).build();
        }

        Resource resource = new UrlResource(videoPath.toUri());
        long contentLength = resource.contentLength();

        ResourceRegion region;
        List<HttpRange> ranges = headers.getRange();
        if (ranges == null || ranges.isEmpty()) {
            long rangeLength = Math.min(CHUNK_SIZE, contentLength);
            region = new ResourceRegion(resource, 0, rangeLength);
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private record TokenCacheEntry(boolean isAdmin, long expiresAtMs) {}
}
