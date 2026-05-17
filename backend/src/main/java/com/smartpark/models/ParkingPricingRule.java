package com.smartpark.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "parking_pricing_rules")
public class ParkingPricingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "parking_id", nullable = false)
    private Parking parking;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private RuleType ruleType;

    @Column(nullable = false)
    private int priority = 100;

    @Column(precision = 10, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "override_rate", precision = 10, scale = 2)
    private BigDecimal overrideRate;

    @Column(name = "days_of_week", length = 60)
    private String daysOfWeek;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "start_datetime")
    private LocalDateTime startDateTime;

    @Column(name = "end_datetime")
    private LocalDateTime endDateTime;

    @Column(name = "min_occupancy_pct")
    private Double minOccupancyPct;

    @Column(name = "max_occupancy_pct")
    private Double maxOccupancyPct;

    public ParkingPricingRule() {}

    public enum RuleType {
        DATE_RANGE,
        TIME_WINDOW,
        OCCUPANCY
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Parking getParking() { return parking; }
    public void setParking(Parking parking) { this.parking = parking; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getOverrideRate() { return overrideRate; }
    public void setOverrideRate(BigDecimal overrideRate) { this.overrideRate = overrideRate; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public LocalDateTime getStartDateTime() { return startDateTime; }
    public void setStartDateTime(LocalDateTime startDateTime) { this.startDateTime = startDateTime; }

    public LocalDateTime getEndDateTime() { return endDateTime; }
    public void setEndDateTime(LocalDateTime endDateTime) { this.endDateTime = endDateTime; }

    public Double getMinOccupancyPct() { return minOccupancyPct; }
    public void setMinOccupancyPct(Double minOccupancyPct) { this.minOccupancyPct = minOccupancyPct; }

    public Double getMaxOccupancyPct() { return maxOccupancyPct; }
    public void setMaxOccupancyPct(Double maxOccupancyPct) { this.maxOccupancyPct = maxOccupancyPct; }
}

