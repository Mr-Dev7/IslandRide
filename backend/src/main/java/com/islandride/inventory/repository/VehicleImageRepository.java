package com.islandride.inventory.repository;

import com.islandride.inventory.domain.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    Optional<VehicleImage> findByPublicId(String publicId);

    List<VehicleImage> findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(Long vehicleId);

    List<VehicleImage> findByVehicleIdInAndPrimaryTrue(Collection<Long> vehicleIds);

    long countByVehicleId(Long vehicleId);
}

