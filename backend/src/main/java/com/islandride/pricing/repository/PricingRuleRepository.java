package com.islandride.pricing.repository;

import com.islandride.pricing.domain.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    List<PricingRule> findByBusinessIdAndActiveTrue(Long businessId);

    List<PricingRule> findByVehicleIdAndActiveTrue(Long vehicleId);

    List<PricingRule> findByCategoryIdAndActiveTrue(Long categoryId);
}

