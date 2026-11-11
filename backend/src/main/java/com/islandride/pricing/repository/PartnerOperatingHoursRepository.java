package com.islandride.pricing.repository;

import com.islandride.pricing.domain.PartnerOperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerOperatingHoursRepository extends JpaRepository<PartnerOperatingHours, Long> {

    List<PartnerOperatingHours> findByBusinessIdOrderByScopeKeyAscDayOfWeekAsc(Long businessId);

    Optional<PartnerOperatingHours> findByBusinessIdAndScopeKeyAndDayOfWeek(Long businessId, String scopeKey, int dayOfWeek);
}

