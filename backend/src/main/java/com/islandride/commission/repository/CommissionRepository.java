package com.islandride.commission.repository;

import com.islandride.commission.domain.Commission;
import com.islandride.commission.domain.CommissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

    Optional<Commission> findByBookingId(Long bookingId);

    List<Commission> findByBusinessId(Long businessId);

    List<Commission> findByBusinessIdAndStatus(Long businessId, CommissionStatus status);

    List<Commission> findByStatus(CommissionStatus status);
}

