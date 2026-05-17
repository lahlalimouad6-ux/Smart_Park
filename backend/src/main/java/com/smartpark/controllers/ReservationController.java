package com.smartpark.controllers;

import com.smartpark.dto.ReservationRequest;
import com.smartpark.models.Parking;
import com.smartpark.models.Reservation;
import com.smartpark.models.User;
import com.smartpark.security.services.UserDetailsImpl;
import com.smartpark.services.ReservationService;
import com.smartpark.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody ReservationRequest request) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Reservation reservation = reservationService.createReservation(userDetails.getId(), request);
            return ResponseEntity.ok(reservation);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my")
    public List<Reservation> getMyReservations() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return reservationRepository.findByUserId(userDetails.getId());
    }

    public record AdminReservationRow(
            Long reservationId,
            Long userId,
            String userNom,
            String userPrenom,
            String userEmail,
            Long parkingId,
            String parkingNom,
            Long spotId,
            String spotNumeroPlace,
            LocalDateTime dateDebut,
            LocalDateTime dateFin,
            BigDecimal montantTotal,
            Reservation.ReservationStatus statut
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

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<List<AdminReservationRow>> getAdminReservationOverview() {
        List<Object[]> rows = reservationRepository.findAdminReservationOverview();
        List<AdminReservationRow> mapped = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            mapped.add(new AdminReservationRow(
                    (Long) r[0],
                    (Long) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    (Long) r[5],
                    (String) r[6],
                    (Long) r[7],
                    (String) r[8],
                    (LocalDateTime) r[9],
                    (LocalDateTime) r[10],
                    (BigDecimal) r[11],
                    (Reservation.ReservationStatus) r[12]
            ));
        }
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/admin/paged")
    @PreAuthorize("hasAnyAuthority('ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getAdminReservationOverviewPaged(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));

        Long reservationId = null;
        String trimmed = q != null ? q.trim() : null;
        if (trimmed != null && !trimmed.isEmpty() && trimmed.matches("\\d+")) {
            try {
                reservationId = Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {}
        }

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Object[]> result = reservationRepository.findAdminReservationOverviewPage(trimmed, reservationId, pageable);

        List<AdminReservationRow> mapped = new ArrayList<>(result.getContent().size());
        for (Object[] r : result.getContent()) {
            mapped.add(new AdminReservationRow(
                    (Long) r[0],
                    (Long) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    (Long) r[5],
                    (String) r[6],
                    (Long) r[7],
                    (String) r[8],
                    (LocalDateTime) r[9],
                    (LocalDateTime) r[10],
                    (BigDecimal) r[11],
                    (Reservation.ReservationStatus) r[12]
            ));
        }

        return ResponseEntity.ok(new PagedResponse<>(
                mapped,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        ));
    }

    @GetMapping("/token/{token}")
    public ResponseEntity<?> getReservationByToken(@PathVariable String token) {
        return reservationRepository.findByQrCodeToken(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        String out = input;
        out = out.replace("&", "&amp;");
        out = out.replace("<", "&lt;");
        out = out.replace(">", "&gt;");
        out = out.replace("\"", "&quot;");
        out = out.replace("'", "&#39;");
        return out;
    }

    @GetMapping(value = "/qr/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> renderReservationQr(@PathVariable String token) {
        if (!StringUtils.hasText(token)) {
            return ResponseEntity.badRequest().body("<html><body>Token invalide</body></html>");
        }

        Reservation reservation = reservationRepository.findByQrCodeTokenWithDetails(token.trim()).orElse(null);
        if (reservation == null) {
            return ResponseEntity.status(404).body("<html><body>Réservation introuvable</body></html>");
        }

        User user = reservation.getUser();
        String conducteurNom = user != null ? (user.getNom() + " " + user.getPrenom()).trim() : "";
        String conducteurEmail = user != null ? user.getEmail() : "";

        String place = reservation.getSpot() != null ? reservation.getSpot().getNumeroPlace() : "";
        Parking parking = (reservation.getSpot() != null
                && reservation.getSpot().getZone() != null
                && reservation.getSpot().getZone().getParking() != null)
                ? reservation.getSpot().getZone().getParking()
                : null;
        String parkingNom = parking != null ? parking.getNom() : "";
        String parkingAdresse = parking != null ? parking.getAdresse() : "";
        String parkingVille = parking != null ? parking.getVille() : "";

        LocalDateTime debut = reservation.getDateDebut();
        LocalDateTime fin = reservation.getDateFin();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String debutStr = debut != null ? debut.format(dtf) : "";
        String finStr = fin != null ? fin.format(dtf) : "";

        boolean active = false;
        LocalDateTime now = LocalDateTime.now();
        if (debut != null && fin != null) {
            active = (now.isEqual(debut) || now.isAfter(debut)) && now.isBefore(fin);
        }
        String badgeText = active ? "VALIDE" : "INACTIF";
        String badgeBg = active ? "#16a34a" : "#f59e0b";

        String html = """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>SmartPark - Accès</title>
                  <style>
                    body{font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,Arial;background:#f6f7fb;margin:0;padding:24px;color:#0f172a}
                    .card{max-width:760px;margin:0 auto;background:#fff;border:1px solid #e5e7eb;border-radius:16px;box-shadow:0 8px 30px rgba(15,23,42,.08);overflow:hidden}
                    .header{padding:18px 20px;background:#2563eb;color:#fff;display:flex;align-items:center;justify-content:space-between}
                    .header h1{margin:0;font-size:18px;letter-spacing:.3px}
                    .badge{font-weight:800;font-size:12px;padding:8px 10px;border-radius:999px;background:%s;color:#fff}
                    .content{padding:20px}
                    .grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
                    .row{padding:12px 14px;border:1px solid #e5e7eb;border-radius:12px;background:#fafafa}
                    .k{font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:#64748b;font-weight:800;margin-bottom:6px}
                    .v{font-size:15px;font-weight:700;color:#0f172a;word-break:break-word}
                    .v2{margin-top:4px;font-size:13px;color:#334155}
                    .footer{padding:14px 20px;border-top:1px solid #e5e7eb;color:#64748b;font-size:12px}
                    @media (max-width:700px){.grid{grid-template-columns:1fr}}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <h1>SmartPark - Détails d'accès</h1>
                      <div class="badge">%s</div>
                    </div>
                    <div class="content">
                      <div class="grid">
                        <div class="row">
                          <div class="k">Conducteur</div>
                          <div class="v">%s</div>
                          <div class="v2">%s</div>
                        </div>
                        <div class="row">
                          <div class="k">Parking</div>
                          <div class="v">%s</div>
                          <div class="v2">%s, %s</div>
                        </div>
                        <div class="row">
                          <div class="k">Place réservée</div>
                          <div class="v">%s</div>
                        </div>
                        <div class="row">
                          <div class="k">Créneau</div>
                          <div class="v">Début : %s</div>
                          <div class="v2">Fin : %s</div>
                        </div>
                        <div class="row">
                          <div class="k">Réservation</div>
                          <div class="v">#%s</div>
                          <div class="v2">Statut : %s</div>
                        </div>
                        <div class="row">
                          <div class="k">Montant</div>
                          <div class="v">%s</div>
                          <div class="v2">Token : %s</div>
                        </div>
                      </div>
                    </div>
                    <div class="footer">
                      Présentez cet écran à l’entrée. En cas de problème, contactez l’administration du parking.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                badgeBg,
                escapeHtml(badgeText),
                escapeHtml(conducteurNom),
                escapeHtml(conducteurEmail),
                escapeHtml(parkingNom),
                escapeHtml(parkingAdresse),
                escapeHtml(parkingVille),
                escapeHtml(place),
                escapeHtml(debutStr),
                escapeHtml(finStr),
                escapeHtml(String.valueOf(reservation.getId())),
                escapeHtml(String.valueOf(reservation.getStatut())),
                escapeHtml(reservation.getMontantTotal() != null ? reservation.getMontantTotal().toString() : ""),
                escapeHtml(reservation.getQrCodeToken())
        );

        return ResponseEntity.ok(html);
    }

    private static List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        if (text == null) return List.of("");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of("");
        if (fm.stringWidth(trimmed) <= maxWidth) return List.of(trimmed);

        String[] words = trimmed.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.isEmpty()) {
                current.append(w);
                continue;
            }
            String candidate = current + " " + w;
            if (fm.stringWidth(candidate) <= maxWidth) {
                current.append(" ").append(w);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(w);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int drawKeyValue(Graphics2D g, int x, int y, int w, String key, String value, Font keyFont, Font valueFont) {
        g.setFont(keyFont);
        g.setColor(new Color(100, 116, 139));
        g.drawString(key, x, y);
        y += g.getFontMetrics().getHeight() + 6;

        g.setFont(valueFont);
        g.setColor(new Color(15, 23, 42));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrapText(value, fm, w);
        for (String line : lines) {
            g.drawString(line, x, y);
            y += fm.getHeight() + 2;
        }
        return y + 10;
    }

    @GetMapping(value = "/qr-image/{token}", produces = MediaType.IMAGE_PNG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> renderReservationQrImage(@PathVariable String token) throws IOException {
        if (!StringUtils.hasText(token)) {
            return ResponseEntity.badRequest().build();
        }

        Reservation reservation = reservationRepository.findByQrCodeTokenWithDetails(token.trim()).orElse(null);
        if (reservation == null) {
            return ResponseEntity.notFound().build();
        }

        User user = reservation.getUser();
        String conducteurNom = user != null ? (user.getNom() + " " + user.getPrenom()).trim() : "";
        String conducteurEmail = user != null ? user.getEmail() : "";

        String place = reservation.getSpot() != null ? reservation.getSpot().getNumeroPlace() : "";
        Parking parking = (reservation.getSpot() != null
                && reservation.getSpot().getZone() != null
                && reservation.getSpot().getZone().getParking() != null)
                ? reservation.getSpot().getZone().getParking()
                : null;
        String parkingNom = parking != null ? parking.getNom() : "";
        String parkingAdresse = parking != null ? parking.getAdresse() : "";
        String parkingVille = parking != null ? parking.getVille() : "";

        LocalDateTime debut = reservation.getDateDebut();
        LocalDateTime fin = reservation.getDateFin();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String debutStr = debut != null ? debut.format(dtf) : "";
        String finStr = fin != null ? fin.format(dtf) : "";

        boolean active = false;
        LocalDateTime now = LocalDateTime.now();
        if (debut != null && fin != null) {
            active = (now.isEqual(debut) || now.isAfter(debut)) && now.isBefore(fin);
        }

        int imgW = 1080;
        int imgH = 1350;
        int pad = 60;
        int cardPad = 46;
        int headerH = 180;

        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(246, 247, 251));
        g.fillRect(0, 0, imgW, imgH);

        int cardX = pad;
        int cardY = pad;
        int cardW = imgW - 2 * pad;
        int cardH = imgH - 2 * pad;

        g.setColor(Color.WHITE);
        g.fillRoundRect(cardX, cardY, cardW, cardH, 36, 36);
        g.setColor(new Color(229, 231, 235));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 36, 36);

        g.setColor(new Color(37, 99, 235));
        g.fillRoundRect(cardX, cardY, cardW, headerH, 36, 36);
        g.fillRect(cardX, cardY + headerH - 36, cardW, 36);

        Font titleFont = new Font("SansSerif", Font.BOLD, 44);
        Font badgeFont = new Font("SansSerif", Font.BOLD, 26);
        Font keyFont = new Font("SansSerif", Font.BOLD, 22);
        Font valueFont = new Font("SansSerif", Font.BOLD, 30);
        Font valueSmallFont = new Font("SansSerif", Font.PLAIN, 26);

        g.setFont(titleFont);
        g.setColor(Color.WHITE);
        int titleX = cardX + cardPad;
        int titleY = cardY + 78;
        g.drawString("SmartPark - Accès", titleX, titleY);

        String badgeText = active ? "VALIDE" : "INACTIF";
        Color badgeColor = active ? new Color(22, 163, 74) : new Color(245, 158, 11);
        int badgeW = 180;
        int badgeH = 54;
        int badgeX = cardX + cardW - cardPad - badgeW;
        int badgeY = cardY + 58;
        g.setColor(badgeColor);
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 999, 999);
        g.setFont(badgeFont);
        g.setColor(Color.WHITE);
        FontMetrics bm = g.getFontMetrics();
        int bx = badgeX + (badgeW - bm.stringWidth(badgeText)) / 2;
        int by = badgeY + (badgeH - bm.getHeight()) / 2 + bm.getAscent();
        g.drawString(badgeText, bx, by);

        int contentX = cardX + cardPad;
        int contentY = cardY + headerH + 46;
        int contentW = cardW - 2 * cardPad;

        contentY = drawKeyValue(g, contentX, contentY, contentW, "CONDUCTEUR", conducteurNom, keyFont, valueFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "EMAIL", conducteurEmail, keyFont, valueSmallFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "PARKING", parkingNom, keyFont, valueFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "ADRESSE", parkingAdresse + (parkingVille.isBlank() ? "" : ", " + parkingVille), keyFont, valueSmallFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "PLACE RÉSERVÉE", place, keyFont, valueFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "DÉBUT", debutStr, keyFont, valueSmallFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "FIN", finStr, keyFont, valueSmallFont);
        contentY = drawKeyValue(g, contentX, contentY, contentW, "RÉSERVATION", "#" + reservation.getId() + " - " + reservation.getStatut(), keyFont, valueSmallFont);

        String amount = reservation.getMontantTotal() != null ? reservation.getMontantTotal().toString() : "";
        contentY = drawKeyValue(g, contentX, contentY, contentW, "MONTANT", amount, keyFont, valueSmallFont);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store, max-age=0")
                .body(bytes);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable Long id) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            Reservation reservation = reservationService.cancelReservation(userDetails.getId(), id);
            return ResponseEntity.ok(reservation);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
