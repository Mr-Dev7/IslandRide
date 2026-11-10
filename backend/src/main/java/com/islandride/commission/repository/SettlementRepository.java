package com.islandride.commission.repository;

import com.islandride.commission.domain.Settlement;
import com.islandride.commission.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByPublicId(String publicId);

    List<Settlement> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<Settlement> findByStatus(SettlementStatus status);
}

