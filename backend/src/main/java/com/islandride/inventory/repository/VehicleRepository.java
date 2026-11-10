package com.islandride.inventory.repository;

import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPublicId(String publicId);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    List<Vehicle> findByBusinessIdAndDeletedAtIsNull(Long businessId);

    List<Vehicle> findByIslandIdAndStatusAndDeletedAtIsNull(Long islandId, VehicleStatus status);
}

