package com.islandride.partner.repository;

import com.islandride.partner.domain.BusinessLocation;
import com.islandride.partner.domain.BusinessLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessLocationRepository extends JpaRepository<BusinessLocation, BusinessLocationId> {

    List<BusinessLocation> findByBusinessId(Long businessId);

    void deleteByBusinessId(Long businessId);
}

