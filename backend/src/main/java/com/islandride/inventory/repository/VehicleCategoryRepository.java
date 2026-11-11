package com.islandride.inventory.repository;

import com.islandride.inventory.domain.VehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, Long> {

    Optional<VehicleCategory> findByCode(String code);

    Optional<VehicleCategory> findBySlug(String slug);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByNameIgnoreCase(String name);

    List<VehicleCategory> findByActiveTrueOrderByDisplayOrderAsc();

    List<VehicleCategory> findAllByOrderByDisplayOrderAsc();
}

