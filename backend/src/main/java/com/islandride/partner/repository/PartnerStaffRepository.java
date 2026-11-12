package com.islandride.partner.repository;

import com.islandride.partner.domain.PartnerStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerStaffRepository extends JpaRepository<PartnerStaff, Long> {

    List<PartnerStaff> findByUserId(Long userId);

    Optional<PartnerStaff> findFirstByUserIdAndActiveTrueOrderByIdAsc(Long userId);

    List<PartnerStaff> findAllByUserIdAndActiveTrue(Long userId);

    Optional<PartnerStaff> findByBusinessIdAndUserId(Long businessId, Long userId);

    List<PartnerStaff> findByBusinessIdAndActiveTrue(Long businessId);
}

