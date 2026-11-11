package com.islandride.pricing.repository;

import com.islandride.pricing.domain.PartnerCancellationTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerCancellationTierRepository extends JpaRepository<PartnerCancellationTier, Long> {

    List<PartnerCancellationTier> findByBusinessIdAndActiveTrueOrderByPriorityAscMinHoursBeforePickupDesc(Long businessId);
}

