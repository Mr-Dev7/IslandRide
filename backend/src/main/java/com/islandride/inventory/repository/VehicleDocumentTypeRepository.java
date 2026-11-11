package com.islandride.inventory.repository;

import com.islandride.inventory.domain.VehicleDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDocumentTypeRepository extends JpaRepository<VehicleDocumentType, Long> {

    Optional<VehicleDocumentType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<VehicleDocumentType> findByActiveTrueOrderByDisplayOrderAsc();

    List<VehicleDocumentType> findAllByOrderByDisplayOrderAsc();
}

