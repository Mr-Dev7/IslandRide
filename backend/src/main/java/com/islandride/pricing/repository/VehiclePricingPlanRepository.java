package com.islandride.pricing.repository;

import com.islandride.pricing.domain.VehiclePricingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehiclePricingPlanRepository extends JpaRepository<VehiclePricingPlan, Long> {

    Optional<VehiclePricingPlan> findByPublicId(String publicId);

    Optional<VehiclePricingPlan> findFirstByVehicleIdAndActiveTrueOrderByUpdatedAtDesc(Long vehicleId);

    List<VehiclePricingPlan> findByVehicleIdOrderByActiveDescCreatedAtDesc(Long vehicleId);

    List<VehiclePricingPlan> findByVehicleIdAndActiveTrue(Long vehicleId);
}

