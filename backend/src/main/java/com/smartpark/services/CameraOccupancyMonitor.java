package com.smartpark.services;

import com.smartpark.models.ParkingCamera;
import com.smartpark.models.Zone;
import com.smartpark.models.ParkingSpot;
import com.smartpark.models.ParkingSpotRegion;
import com.smartpark.repository.ParkingCameraRepository;
import com.smartpark.repository.ParkingSpotRegionRepository;
import com.smartpark.repository.ParkingSpotRepository;
import com.smartpark.repository.ZoneRepository;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CameraOccupancyMonitor {
    private static final int HIST_BINS = 16;
    private static final int MAX_TRAIN_IMAGES_PER_CLASS = 250;
    private static final int SAMPLE_STEP = 6;
    private static final int MAX_AUTO_SPOTS = 500;
    private static final int CONFIRM_OCCUPIED_FRAMES = 1;
    private static final int CONFIRM_FREE_FRAMES = 1;
    private static final double GLOBAL_INIT_CONFIDENCE = 0.003;
    private static final double GLOBAL_UPDATE_CONFIDENCE = 0.0015;
    private static final double BG_ALPHA = 0.06;
    private static final double BG_MIN_THRESHOLD = 0.010;
    private static final double HEUR_BRIGHT_FREE = 0.28;
    private static final double HEUR_DARK_FREE_MAX = 0.18;
    private static final double HEUR_DARK_OCC = 0.30;
    private static final double HEUR_CONF_STRONG = 0.0020;
    private static final double INNER_CROP_RATIO = 0.08;

    @Autowired
    private ParkingCameraRepository parkingCameraRepository;

    @Autowired
    private ParkingSpotRegionRepository regionRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    private final Object modelLock = new Object();
    private volatile HistogramModel model;

    private final Map<Long, Double> cursorSecondsByCameraId = new HashMap<>();
    private final ConcurrentHashMap<Long, Long> lastTickNanosByCameraId = new ConcurrentHashMap<>();
    private final Set<Long> layoutAppliedForParkingId = Collections.synchronizedSet(new HashSet<>());
    private volatile List<Candidate> maskCandidates;
    private final ConcurrentHashMap<Long, SpotTemporal> temporalBySpotId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SpotBackground> backgroundBySpotId = new ConcurrentHashMap<>();

    private Path datasetParkingDir() {
        return Path.of(System.getProperty("user.dir")).resolve("..").resolve("datasets").resolve("parking").normalize();
    }

    private Path maskPath() {
        return datasetParkingDir().resolve("mask_1920_1080.png").normalize();
    }

    @Scheduled(fixedDelayString = "${smartpark.camera.monitor.delay-ms:500}")
    @Transactional
    public void monitor() {
        HistogramModel m = model;
        if (m == null) {
            initModelIfPossible();
            m = model;
        }
        if (m == null) {
            return;
        }

        List<ParkingCamera> cameras = parkingCameraRepository.findAll();
        for (ParkingCamera cam : cameras) {
            if (cam == null || cam.getId() == null || cam.getParking() == null || cam.getParking().getId() == null) continue;
            if (!StringUtils.hasText(cam.getVideoFile())) continue;
            monitorOne(cam, m);
        }
    }

    public Map<Long, ParkingSpot.SpotStatus> predictLiveStatuses(ParkingCamera cam, List<ParkingSpotRegion> regions, double second) {
        if (cam == null || cam.getId() == null || cam.getParking() == null || cam.getParking().getId() == null) return Map.of();
        if (!StringUtils.hasText(cam.getVideoFile())) return Map.of();
        if (regions == null || regions.isEmpty()) return Map.of();

        HistogramModel m = model;
        if (m == null) {
            initModelIfPossible();
            m = model;
        }
        if (m == null) return Map.of();

        Path base = datasetParkingDir();
        Path videoPath = base.resolve(cam.getVideoFile()).normalize();
        if (!videoPath.startsWith(base) || !Files.exists(videoPath)) return Map.of();

        BufferedImage frame = grabFrame(videoPath, Math.max(0.0, second));
        if (frame == null) return Map.of();

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        Map<Long, ParkingSpot.SpotStatus> out = new HashMap<>(regions.size());
        for (ParkingSpotRegion region : regions) {
            if (region == null || region.getSpot() == null || region.getSpot().getId() == null) continue;
            ParkingSpot spot = region.getSpot();
            Long spotId = spot.getId();

            ParkingSpot.SpotStatus current = spot.getStatut();
            if (current == ParkingSpot.SpotStatus.RESERVE) {
                out.put(spotId, ParkingSpot.SpotStatus.RESERVE);
                continue;
            }

            double nx = region.getX();
            double ny = region.getY();
            double nw = region.getW();
            double nh = region.getH();

            double padX = nw * INNER_CROP_RATIO;
            double padY = nh * INNER_CROP_RATIO;
            nx = nx + padX;
            ny = ny + padY;
            nw = Math.max(0.0, nw - 2.0 * padX);
            nh = Math.max(0.0, nh - 2.0 * padY);

            BufferedImage crop = crop(frame, fw, fh, nx, ny, nw, nh);
            if (crop == null) {
                out.put(spotId, current);
                continue;
            }

            double[] hist = histogram(crop);
            Score score = m.score(hist);
            double conf = Math.abs(score.de() - score.dn());
            int vote = heuristicVote(hist);
            boolean occupiedPred = vote > 0 || (vote == 0 && conf >= HEUR_CONF_STRONG && score.dn() < score.de());

            SpotBackground bg = backgroundBySpotId.computeIfAbsent(spotId, (k) -> new SpotBackground());
            if (!bg.hasBaseline()) {
                if (vote < 0 || (!occupiedPred && conf >= GLOBAL_INIT_CONFIDENCE)) {
                    bg.init(hist);
                }
            }
            if (bg.hasBaseline()) {
                double delta = bg.delta(hist);
                double threshold = bg.threshold();
                if (vote == 0) {
                    occupiedPred = delta > threshold;
                }
                if (!occupiedPred && (vote < 0 || conf >= GLOBAL_UPDATE_CONFIDENCE)) {
                    bg.observeFree(hist, delta, BG_ALPHA);
                }
            }

            out.put(spotId, occupiedPred ? ParkingSpot.SpotStatus.OCCUPE : ParkingSpot.SpotStatus.LIBRE);
        }
        return out;
    }

    @Transactional
    public void ensureLayoutForParking(Long parkingId) {
        if (parkingId == null) return;
        if (model == null) initModelIfPossible();
        HistogramModel m = model;
        if (m == null) return;

        boolean forceFromMask = false;
        List<Candidate> mask = getMaskCandidates();
        if (mask != null && !mask.isEmpty()) {
            forceFromMask = true;
        }

        List<ParkingSpotRegion> existing = regionRepository.findBySpotZoneParkingId(parkingId);
        if (!forceFromMask && existing != null && !existing.isEmpty()) return;
        if (forceFromMask && existing != null && !existing.isEmpty() && layoutAppliedForParkingId.contains(parkingId)) return;

        ParkingCamera cam = parkingCameraRepository.findByParkingId(parkingId).orElse(null);
        if (cam == null || cam.getParking() == null) return;
        if (!StringUtils.hasText(cam.getVideoFile())) return;

        Path base = datasetParkingDir();
        Path videoPath = base.resolve(cam.getVideoFile()).normalize();
        if (!videoPath.startsWith(base) || !Files.exists(videoPath)) return;

        Zone zone = ensureZone(parkingId, cam);
        if (zone == null) return;

        List<Candidate> candidates;
        if (forceFromMask) {
            candidates = mask;
        } else {
            BufferedImage frame = grabFrame(videoPath, 0.0);
            if (frame == null) return;
            candidates = discoverCandidates(frame, m);
            if (candidates.isEmpty()) {
                candidates = fallbackGridCandidates(4, 4);
            }
        }

        List<Candidate> limited = candidates.stream().limit(MAX_AUTO_SPOTS).toList();

        List<ParkingSpot> existingSpots = spotRepository.findByZoneParkingId(parkingId);
        existingSpots.sort(Comparator.comparing(ParkingSpot::getNumeroPlace, Comparator.nullsLast(String::compareTo)));

        Set<Long> assignedSpotIds = new HashSet<>(limited.size());
        int index = 1;
        for (Candidate c : limited) {
            ParkingSpot spot;
            if (index <= existingSpots.size()) {
                spot = existingSpots.get(index - 1);
            } else {
                ParkingSpot created = new ParkingSpot();
                created.setNumeroPlace(String.format("A%02d", index));
                created.setType(ParkingSpot.SpotType.STANDARD);
                created.setStatut(ParkingSpot.SpotStatus.LIBRE);
                created.setCoordX(((index - 1) % 8) * 60);
                created.setCoordY(((index - 1) / 8) * 60);
                created.setZone(zone);
                spot = spotRepository.save(created);
            }

            String desiredName = String.format("A%02d", index);
            if (spot.getNumeroPlace() == null || !spot.getNumeroPlace().equals(desiredName)) {
                spot.setNumeroPlace(desiredName);
                spotRepository.save(spot);
            }

            ParkingSpotRegion region = regionRepository.findBySpotId(spot.getId()).orElse(null);
            if (region == null) {
                region = new ParkingSpotRegion();
                region.setSpot(spot);
            }
            region.setX(c.x());
            region.setY(c.y());
            region.setW(c.w());
            region.setH(c.h());
            regionRepository.save(region);
            assignedSpotIds.add(spot.getId());

            index++;
        }

        if (forceFromMask) {
            List<ParkingSpotRegion> all = regionRepository.findBySpotZoneParkingId(parkingId);
            List<ParkingSpotRegion> extra = new ArrayList<>();
            for (ParkingSpotRegion r : all) {
                if (r == null || r.getSpot() == null || r.getSpot().getId() == null) continue;
                if (!assignedSpotIds.contains(r.getSpot().getId())) {
                    extra.add(r);
                }
            }
            if (!extra.isEmpty()) {
                regionRepository.deleteAll(extra);
            }
            layoutAppliedForParkingId.add(parkingId);
        }
    }

    private List<Candidate> getMaskCandidates() {
        List<Candidate> cached = maskCandidates;
        if (cached != null) return cached;
        Path path = maskPath();
        if (!Files.exists(path)) {
            maskCandidates = List.of();
            return maskCandidates;
        }
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) {
                maskCandidates = List.of();
                return maskCandidates;
            }
            List<Candidate> parsed = parseMask(img);
            maskCandidates = parsed;
            return parsed;
        } catch (IOException e) {
            maskCandidates = List.of();
            return maskCandidates;
        }
    }

    private static List<Candidate> parseMask(BufferedImage mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int[] labels = new int[w * h];
        int[] parent = new int[4096];
        parent[0] = 0;
        int nextLabel = 1;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            int upRow = (y - 1) * w;
            for (int x = 0; x < w; x++) {
                int idx = row + x;
                if (!isWhite(mask.getRGB(x, y))) continue;
                int left = x > 0 ? labels[idx - 1] : 0;
                int up = y > 0 ? labels[upRow + x] : 0;
                if (left == 0 && up == 0) {
                    if (nextLabel >= parent.length) {
                        int[] grown = new int[parent.length * 2];
                        System.arraycopy(parent, 0, grown, 0, parent.length);
                        parent = grown;
                    }
                    parent[nextLabel] = nextLabel;
                    labels[idx] = nextLabel;
                    nextLabel++;
                } else if (left != 0 && up == 0) {
                    labels[idx] = left;
                } else if (left == 0) {
                    labels[idx] = up;
                } else {
                    labels[idx] = Math.min(left, up);
                    union(parent, left, up);
                }
            }
        }

        int[] minX = new int[nextLabel];
        int[] minY = new int[nextLabel];
        int[] maxX = new int[nextLabel];
        int[] maxY = new int[nextLabel];
        int[] count = new int[nextLabel];
        for (int i = 1; i < nextLabel; i++) {
            minX[i] = Integer.MAX_VALUE;
            minY[i] = Integer.MAX_VALUE;
            maxX[i] = -1;
            maxY[i] = -1;
        }

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int idx = row + x;
                int lab = labels[idx];
                if (lab == 0) continue;
                int root = find(parent, lab);
                labels[idx] = root;
                count[root]++;
                if (x < minX[root]) minX[root] = x;
                if (y < minY[root]) minY[root] = y;
                if (x > maxX[root]) maxX[root] = x;
                if (y > maxY[root]) maxY[root] = y;
            }
        }

        List<Candidate> out = new ArrayList<>();
        for (int i = 1; i < nextLabel; i++) {
            if (count[i] == 0) continue;
            int bx1 = minX[i];
            int by1 = minY[i];
            int bx2 = maxX[i];
            int by2 = maxY[i];
            int bw = bx2 - bx1 + 1;
            int bh = by2 - by1 + 1;
            if (bw < 8 || bh < 8) continue;
            int area = bw * bh;
            double fill = (double) count[i] / (double) area;
            if (area < 200 || area > 200000) continue;
            if (fill < 0.6) continue;

            double nx = (double) bx1 / (double) w;
            double ny = (double) by1 / (double) h;
            double nw = (double) bw / (double) w;
            double nh = (double) bh / (double) h;
            out.add(new Candidate(nx, ny, nw, nh, 1.0));
        }

        out.sort(Comparator.comparingDouble(Candidate::y).thenComparingDouble(Candidate::x));
        return out;
    }

    private static boolean isWhite(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r > 200 && g > 200 && b > 200;
    }

    private static int find(int[] parent, int x) {
        int p = parent[x];
        if (p == x) return x;
        parent[x] = find(parent, p);
        return parent[x];
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return;
        if (ra < rb) parent[rb] = ra;
        else parent[ra] = rb;
    }

    private void initModelIfPossible() {
        synchronized (modelLock) {
            if (model != null) return;

            Path base = datasetParkingDir().resolve("clf-data").normalize();
            Path emptyDir = base.resolve("empty");
            Path notEmptyDir = base.resolve("not_empty");
            if (!Files.isDirectory(emptyDir) || !Files.isDirectory(notEmptyDir)) {
                return;
            }

            HistogramModel loaded = HistogramModel.load(emptyDir, notEmptyDir, MAX_TRAIN_IMAGES_PER_CLASS);
            if (loaded != null) {
                model = loaded;
            }
        }
    }

    private void monitorOne(ParkingCamera cam, HistogramModel m) {
        Long cameraId = cam.getId();
        Long parkingId = cam.getParking().getId();
        List<ParkingSpotRegion> regions = regionRepository.findBySpotZoneParkingId(parkingId);
        if (regions == null || regions.isEmpty()) {
            ensureLayoutForParking(parkingId);
            regions = regionRepository.findBySpotZoneParkingId(parkingId);
            if (regions == null || regions.isEmpty()) return;
        }

        Path base = datasetParkingDir();
        Path videoPath = base.resolve(cam.getVideoFile()).normalize();
        if (!videoPath.startsWith(base) || !Files.exists(videoPath)) return;

        long now = System.nanoTime();
        Long prevTick = lastTickNanosByCameraId.put(cameraId, now);
        double elapsedSeconds = prevTick == null ? 0.25 : (double) (now - prevTick) / 1_000_000_000.0;
        elapsedSeconds = Math.max(0.12, Math.min(1.0, elapsedSeconds));

        double cursorSecond = cursorSecondsByCameraId.getOrDefault(cameraId, 0.0);
        double nextSecond = cursorSecond + elapsedSeconds;
        BufferedImage frame = grabFrame(videoPath, nextSecond);
        if (frame == null) {
            cursorSecondsByCameraId.put(cameraId, 0.0);
            return;
        }
        cursorSecondsByCameraId.put(cameraId, nextSecond);

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        List<ParkingSpot> toSave = new ArrayList<>();
        for (ParkingSpotRegion region : regions) {
            if (region == null || region.getSpot() == null || region.getSpot().getId() == null) continue;
            ParkingSpot spot = region.getSpot();
            if (spot.getStatut() == ParkingSpot.SpotStatus.RESERVE) continue;

            double nx = region.getX();
            double ny = region.getY();
            double nw = region.getW();
            double nh = region.getH();

            double padX = nw * INNER_CROP_RATIO;
            double padY = nh * INNER_CROP_RATIO;
            nx = nx + padX;
            ny = ny + padY;
            nw = Math.max(0.0, nw - 2.0 * padX);
            nh = Math.max(0.0, nh - 2.0 * padY);

            BufferedImage crop = crop(frame, fw, fh, nx, ny, nw, nh);
            if (crop == null) continue;

            double[] hist = histogram(crop);
            Score score = m.score(hist);
            double conf = Math.abs(score.de() - score.dn());
            int vote = heuristicVote(hist);
            boolean occupiedPred = vote > 0 || (vote == 0 && conf >= HEUR_CONF_STRONG && score.dn() < score.de());

            SpotBackground bg = backgroundBySpotId.computeIfAbsent(spot.getId(), (k) -> new SpotBackground());
            if (!bg.hasBaseline()) {
                if (vote < 0 || (!occupiedPred && conf >= GLOBAL_INIT_CONFIDENCE)) {
                    bg.init(hist);
                }
            }
            if (bg.hasBaseline()) {
                double delta = bg.delta(hist);
                double threshold = bg.threshold();
                if (vote == 0) {
                    occupiedPred = delta > threshold;
                }
                if (!occupiedPred && (vote < 0 || conf >= GLOBAL_UPDATE_CONFIDENCE)) {
                    bg.observeFree(hist, delta, BG_ALPHA);
                }
            }

            SpotTemporal temporal = temporalBySpotId.computeIfAbsent(spot.getId(), (k) -> new SpotTemporal());
            ParkingSpot.SpotStatus desired = temporal.nextStatus(occupiedPred, spot.getStatut());
            if (desired != null && spot.getStatut() != desired) {
                spot.setStatut(desired);
                toSave.add(spot);
            }
        }

        if (!toSave.isEmpty()) {
            spotRepository.saveAll(toSave);
        }
    }

    private static final class SpotTemporal {
        private int occupiedStreak;
        private int freeStreak;

        ParkingSpot.SpotStatus nextStatus(boolean occupiedPred, ParkingSpot.SpotStatus current) {
            if (occupiedPred) {
                occupiedStreak = Math.min(CONFIRM_OCCUPIED_FRAMES, occupiedStreak + 1);
                freeStreak = 0;
                if (occupiedStreak >= CONFIRM_OCCUPIED_FRAMES) return ParkingSpot.SpotStatus.OCCUPE;
                if (current == ParkingSpot.SpotStatus.OCCUPE) return null;
                return null;
            } else {
                freeStreak = Math.min(CONFIRM_FREE_FRAMES, freeStreak + 1);
                occupiedStreak = 0;
                if (freeStreak >= CONFIRM_FREE_FRAMES) return ParkingSpot.SpotStatus.LIBRE;
                if (current == ParkingSpot.SpotStatus.LIBRE) return null;
                return null;
            }
        }
    }

    private static final class SpotBackground {
        private double[] baselineHist;
        private double meanDelta;
        private double m2Delta;
        private long samples;

        boolean hasBaseline() {
            return baselineHist != null;
        }

        void init(double[] hist) {
            baselineHist = hist.clone();
            meanDelta = 0.0;
            m2Delta = 0.0;
            samples = 0;
        }

        double delta(double[] hist) {
            if (baselineHist == null) return 0.0;
            return l2(hist, baselineHist);
        }

        double threshold() {
            if (samples < 12) return BG_MIN_THRESHOLD;
            double variance = samples > 1 ? (m2Delta / (double) (samples - 1)) : 0.0;
            double std = Math.sqrt(Math.max(0.0, variance));
            return Math.max(BG_MIN_THRESHOLD, meanDelta + 4.0 * std + 0.001);
        }

        void observeFree(double[] hist, double delta, double alpha) {
            if (baselineHist == null) {
                init(hist);
                return;
            }

            for (int i = 0; i < HIST_BINS; i++) {
                baselineHist[i] = baselineHist[i] * (1.0 - alpha) + hist[i] * alpha;
            }

            samples++;
            double d = delta - meanDelta;
            meanDelta += d / (double) samples;
            double d2 = delta - meanDelta;
            m2Delta += d * d2;
        }
    }

    private Zone ensureZone(Long parkingId, ParkingCamera cam) {
        List<Zone> zones = zoneRepository.findByParkingId(parkingId);
        if (zones != null && !zones.isEmpty()) return zones.get(0);
        Zone z = new Zone();
        z.setNomZone("Zone A");
        z.setParking(cam.getParking());
        return zoneRepository.save(z);
    }

    private static List<Candidate> fallbackGridCandidates(int rows, int cols) {
        double marginX = 0.06;
        double marginY = 0.12;
        double usableW = 1.0 - marginX * 2;
        double usableH = 1.0 - marginY * 2;
        double cellW = usableW / cols;
        double cellH = usableH / rows;
        double w = cellW * 0.85;
        double h = cellH * 0.75;
        List<Candidate> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = marginX + c * cellW + (cellW - w) / 2.0;
                double y = marginY + r * cellH + (cellH - h) / 2.0;
                out.add(new Candidate(x, y, w, h, 1.0));
            }
        }
        return out;
    }

    private static List<Candidate> discoverCandidates(BufferedImage frame, HistogramModel m) {
        int fw = frame.getWidth();
        int fh = frame.getHeight();
        if (fw <= 0 || fh <= 0) return List.of();

        double[] ws = new double[]{0.10, 0.12, 0.14};
        double[] hs = new double[]{0.12, 0.16, 0.20};
        double step = 0.06;

        List<Candidate> raw = new ArrayList<>();
        for (double w : ws) {
            for (double h : hs) {
                for (double y = 0.02; y + h <= 0.98; y += step) {
                    for (double x = 0.02; x + w <= 0.98; x += step) {
                        BufferedImage crop = crop(frame, fw, fh, x, y, w, h);
                        if (crop == null) continue;
                        Score s = m.score(crop);
                        double minDist = Math.min(s.de(), s.dn());
                        double conf = Math.abs(s.de() - s.dn());
                        double score = conf / (minDist + 1e-9);
                        if (minDist > 0.04) continue;
                        if (conf < 0.002) continue;
                        if (score < 0.08) continue;
                        raw.add(new Candidate(x, y, w, h, score));
                    }
                }
            }
        }

        raw.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<Candidate> picked = new ArrayList<>();
        for (Candidate c : raw) {
            boolean overlaps = false;
            for (Candidate p : picked) {
                if (iou(c, p) > 0.35) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) picked.add(c);
            if (picked.size() >= MAX_AUTO_SPOTS) break;
        }

        picked.sort(Comparator.comparingDouble(Candidate::y).thenComparingDouble(Candidate::x));
        return picked;
    }

    private static double iou(Candidate a, Candidate b) {
        double ax1 = a.x();
        double ay1 = a.y();
        double ax2 = a.x() + a.w();
        double ay2 = a.y() + a.h();
        double bx1 = b.x();
        double by1 = b.y();
        double bx2 = b.x() + b.w();
        double by2 = b.y() + b.h();

        double ix1 = Math.max(ax1, bx1);
        double iy1 = Math.max(ay1, by1);
        double ix2 = Math.min(ax2, bx2);
        double iy2 = Math.min(ay2, by2);
        double iw = Math.max(0.0, ix2 - ix1);
        double ih = Math.max(0.0, iy2 - iy1);
        double inter = iw * ih;
        if (inter <= 0.0) return 0.0;
        double areaA = (ax2 - ax1) * (ay2 - ay1);
        double areaB = (bx2 - bx1) * (by2 - by1);
        double union = areaA + areaB - inter;
        return union > 0.0 ? inter / union : 0.0;
    }

    private static BufferedImage grabFrame(Path videoPath, double second) {
        try (SeekableByteChannel ch = NIOUtils.readableChannel(videoPath.toFile())) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            grab.seekToSecondSloppy(Math.max(0.0, second));
            Picture pic = grab.getNativeFrame();
            if (pic == null) return null;
            return AWTUtil.toBufferedImage(pic);
        } catch (IOException | JCodecException e) {
            return null;
        }
    }

    private static BufferedImage crop(BufferedImage frame, int fw, int fh, double nx, double ny, double nw, double nh) {
        if (fw <= 0 || fh <= 0) return null;
        int x = (int) Math.floor(Math.max(0.0, Math.min(1.0, nx)) * fw);
        int y = (int) Math.floor(Math.max(0.0, Math.min(1.0, ny)) * fh);
        int w = (int) Math.floor(Math.max(0.0, Math.min(1.0, nw)) * fw);
        int h = (int) Math.floor(Math.max(0.0, Math.min(1.0, nh)) * fh);

        if (w < 2 || h < 2) return null;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + w > fw) w = fw - x;
        if (y + h > fh) h = fh - y;
        if (w < 2 || h < 2) return null;

        try {
            return frame.getSubimage(x, y, w, h);
        } catch (RasterFormatException e) {
            return null;
        }
    }

    private static double[] histogram(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[] hist = new double[HIST_BINS];
        long total = 0;

        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 64);
        int step = Math.max(SAMPLE_STEP, Math.min(stepX, stepY));

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r * 30 + g * 59 + b * 11) / 100;
                int bin = Math.max(0, Math.min(HIST_BINS - 1, gray / (256 / HIST_BINS)));
                hist[bin] += 1.0;
                total++;
            }
        }

        if (total == 0) return hist;
        for (int i = 0; i < HIST_BINS; i++) hist[i] /= total;
        return hist;
    }

    private static double l2(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < HIST_BINS; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    private static int heuristicVote(double[] hist) {
        double dark = 0.0;
        for (int i = 0; i <= 3 && i < HIST_BINS; i++) dark += hist[i];

        double bright = 0.0;
        for (int i = Math.max(0, HIST_BINS - 6); i < HIST_BINS; i++) bright += hist[i];

        if (bright >= HEUR_BRIGHT_FREE && dark <= HEUR_DARK_FREE_MAX) return -1;
        if (dark >= HEUR_DARK_OCC) return 1;
        return 0;
    }

    private record Candidate(double x, double y, double w, double h, double score) {}

    private static final class HistogramModel {
        private final double[] emptyMean;
        private final double[] notEmptyMean;

        private HistogramModel(double[] emptyMean, double[] notEmptyMean) {
            this.emptyMean = emptyMean;
            this.notEmptyMean = notEmptyMean;
        }

        static HistogramModel load(Path emptyDir, Path notEmptyDir, int maxPerClass) {
            double[] empty = meanHistogram(emptyDir, maxPerClass);
            double[] notEmpty = meanHistogram(notEmptyDir, maxPerClass);
            if (empty == null || notEmpty == null) return null;
            return new HistogramModel(empty, notEmpty);
        }

        Score score(double[] h) {
            double de = l2(h, emptyMean);
            double dn = l2(h, notEmptyMean);
            return new Score(de, dn);
        }

        Score score(BufferedImage img) {
            return score(histogram(img));
        }

        private static double[] meanHistogram(Path dir, int maxFiles) {
            if (!Files.isDirectory(dir)) return null;
            double[] sum = new double[HIST_BINS];
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jpg")) {
                for (Path p : stream) {
                    BufferedImage img;
                    try {
                        img = ImageIO.read(p.toFile());
                    } catch (IOException e) {
                        continue;
                    }
                    if (img == null) continue;
                    double[] h = histogram(img);
                    for (int i = 0; i < HIST_BINS; i++) sum[i] += h[i];
                    count++;
                    if (count >= maxFiles) break;
                }
            } catch (IOException e) {
                return null;
            }
            if (count == 0) return null;
            for (int i = 0; i < HIST_BINS; i++) sum[i] /= count;
            return sum;
        }
    }

    private record Score(double de, double dn) {}
}
