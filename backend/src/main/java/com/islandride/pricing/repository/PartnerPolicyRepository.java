package com.islandride.pricing.repository;

import com.islandride.pricing.domain.PartnerPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerPolicyRepository extends JpaRepository<PartnerPolicy, Long> {

    Optional<PartnerPolicy> findByBusinessId(Long businessId);
}

