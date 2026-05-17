package com.smartpark.repository;

import com.smartpark.models.ParkingPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParkingPricingRuleRepository extends JpaRepository<ParkingPricingRule, Long> {
    List<ParkingPricingRule> findByParkingIdAndEnabledTrueOrderByPriorityAscIdAsc(Long parkingId);
    List<ParkingPricingRule> findByParkingIdOrderByPriorityAscIdAsc(Long parkingId);
}

