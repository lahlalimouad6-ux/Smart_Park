package com.smartpark.services;

import com.smartpark.models.Parking;
import com.smartpark.models.ParkingPricingRule;
import com.smartpark.models.ParkingSpot;
import com.smartpark.repository.ParkingPricingRuleRepository;
import com.smartpark.repository.ParkingSpotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PricingService {
    @Autowired
    private ParkingPricingRuleRepository pricingRuleRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    public record AppliedRule(
            Long id,
            String name,
            ParkingPricingRule.RuleType type,
            int priority,
            BigDecimal multiplier,
            BigDecimal overrideRate
    ) {}

    public record Quote(
            Long parkingId,
            Long spotId,
            BigDecimal baseHourlyRate,
            BigDecimal totalAmount,
            int totalMinutes,
            BigDecimal minHourlyRate,
            BigDecimal maxHourlyRate,
            List<AppliedRule> appliedRules
    ) {}

    public Quote quoteForSpot(Long spotId, LocalDateTime start, LocalDateTime end) {
        if (spotId == null || start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Paramètres invalides");
        }

        ParkingSpot spot = spotRepository.findById(spotId).orElse(null);
        if (spot == null || spot.getZone() == null || spot.getZone().getParking() == null) {
            throw new IllegalArgumentException("Place introuvable");
        }

        Parking parking = spot.getZone().getParking();
        if (parking.getTarifHeure() == null) {
            throw new IllegalArgumentException("Tarif du parking manquant");
        }

        Long parkingId = parking.getId();
        BigDecimal base = parking.getTarifHeure();
        List<ParkingPricingRule> rules = pricingRuleRepository.findByParkingIdAndEnabledTrueOrderByPriorityAscIdAsc(parkingId);

        long total = spotRepository.countByZoneParkingId(parkingId);
        long occupied = spotRepository.countByZoneParkingIdAndStatut(parkingId, ParkingSpot.SpotStatus.OCCUPE);
        double occupancyPct = total > 0 ? ((double) occupied / (double) total) * 100.0 : 0.0;

        LocalDateTime cursor = start;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal minRate = null;
        BigDecimal maxRate = null;
        Set<Long> appliedRuleIds = new HashSet<>();
        List<AppliedRule> applied = new ArrayList<>();

        while (cursor.isBefore(end)) {
            LocalDateTime segEnd = cursor.plusMinutes(15);
            if (segEnd.isAfter(end)) segEnd = end;

            long segMinutes = Duration.between(cursor, segEnd).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(segMinutes)
                    .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);

            ParkingPricingRule dateRule = bestRule(rules, ParkingPricingRule.RuleType.DATE_RANGE, cursor, occupancyPct);
            ParkingPricingRule timeRule = bestRule(rules, ParkingPricingRule.RuleType.TIME_WINDOW, cursor, occupancyPct);
            ParkingPricingRule occRule = bestRule(rules, ParkingPricingRule.RuleType.OCCUPANCY, cursor, occupancyPct);

            BigDecimal rate = base;
            rate = apply(rate, dateRule);
            rate = apply(rate, timeRule);
            rate = apply(rate, occRule);

            if (minRate == null || rate.compareTo(minRate) < 0) minRate = rate;
            if (maxRate == null || rate.compareTo(maxRate) > 0) maxRate = rate;

            totalAmount = totalAmount.add(rate.multiply(hours));

            if (dateRule != null) collectApplied(dateRule, appliedRuleIds, applied);
            if (timeRule != null) collectApplied(timeRule, appliedRuleIds, applied);
            if (occRule != null) collectApplied(occRule, appliedRuleIds, applied);

            cursor = segEnd;
        }

        int totalMinutes = (int) Math.max(0, Duration.between(start, end).toMinutes());
        return new Quote(
                parkingId,
                spotId,
                base,
                totalAmount.setScale(2, RoundingMode.HALF_UP),
                totalMinutes,
                minRate != null ? minRate.setScale(2, RoundingMode.HALF_UP) : base.setScale(2, RoundingMode.HALF_UP),
                maxRate != null ? maxRate.setScale(2, RoundingMode.HALF_UP) : base.setScale(2, RoundingMode.HALF_UP),
                applied
        );
    }

    private static void collectApplied(ParkingPricingRule rule, Set<Long> appliedRuleIds, List<AppliedRule> applied) {
        if (rule.getId() == null) return;
        if (!appliedRuleIds.add(rule.getId())) return;
        applied.add(new AppliedRule(
                rule.getId(),
                rule.getName(),
                rule.getRuleType(),
                rule.getPriority(),
                rule.getMultiplier(),
                rule.getOverrideRate()
        ));
    }

    private static BigDecimal apply(BigDecimal currentRate, ParkingPricingRule rule) {
        if (rule == null) return currentRate;
        if (rule.getOverrideRate() != null) return rule.getOverrideRate();
        if (rule.getMultiplier() == null) return currentRate;
        return currentRate.multiply(rule.getMultiplier());
    }

    private static ParkingPricingRule bestRule(List<ParkingPricingRule> rules, ParkingPricingRule.RuleType type, LocalDateTime t, double occupancyPct) {
        if (rules == null || rules.isEmpty()) return null;
        ParkingPricingRule best = null;
        for (ParkingPricingRule r : rules) {
            if (r == null || r.getRuleType() != type) continue;
            if (!matches(r, t, occupancyPct)) continue;
            if (best == null) {
                best = r;
                continue;
            }
            if (r.getPriority() < best.getPriority()) {
                best = r;
                continue;
            }
            if (r.getPriority() == best.getPriority() && r.getId() != null && best.getId() != null && r.getId() < best.getId()) {
                best = r;
            }
        }
        return best;
    }

    private static boolean matches(ParkingPricingRule rule, LocalDateTime t, double occupancyPct) {
        if (rule.getRuleType() == ParkingPricingRule.RuleType.DATE_RANGE) {
            if (rule.getStartDateTime() == null || rule.getEndDateTime() == null) return false;
            return (t.isEqual(rule.getStartDateTime()) || t.isAfter(rule.getStartDateTime())) && t.isBefore(rule.getEndDateTime());
        }
        if (rule.getRuleType() == ParkingPricingRule.RuleType.TIME_WINDOW) {
            if (rule.getStartTime() == null || rule.getEndTime() == null) return false;
            EnumSet<DayOfWeek> days = parseDays(rule.getDaysOfWeek());
            if (days != null && !days.contains(t.getDayOfWeek())) return false;
            return timeInWindow(t.toLocalTime(), rule.getStartTime(), rule.getEndTime());
        }
        if (rule.getRuleType() == ParkingPricingRule.RuleType.OCCUPANCY) {
            Double min = rule.getMinOccupancyPct();
            Double max = rule.getMaxOccupancyPct();
            if (min != null && occupancyPct < min) return false;
            if (max != null && occupancyPct > max) return false;
            return true;
        }
        return false;
    }

    private static boolean timeInWindow(LocalTime t, LocalTime start, LocalTime end) {
        if (start.equals(end)) return true;
        if (start.isBefore(end)) {
            return (t.equals(start) || t.isAfter(start)) && t.isBefore(end);
        }
        return (t.equals(start) || t.isAfter(start)) || t.isBefore(end);
    }

    private static EnumSet<DayOfWeek> parseDays(String daysOfWeek) {
        if (!StringUtils.hasText(daysOfWeek)) return null;
        String[] parts = daysOfWeek.split(",");
        EnumSet<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String p : parts) {
            if (!StringUtils.hasText(p)) continue;
            String norm = p.trim().toUpperCase(Locale.ROOT);
            try {
                set.add(DayOfWeek.valueOf(norm));
            } catch (IllegalArgumentException ignored) {}
        }
        return set.isEmpty() ? null : set;
    }
}

