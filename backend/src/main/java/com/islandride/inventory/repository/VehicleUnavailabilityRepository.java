package com.islandride.inventory.repository;

import com.islandride.inventory.domain.AvailabilityBlockStatus;
import com.islandride.inventory.domain.VehicleUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleUnavailabilityRepository extends JpaRepository<VehicleUnavailability, Long> {

    Optional<VehicleUnavailability> findByPublicId(String publicId);

    List<VehicleUnavailability> findByVehicleIdOrderByStartTimeAsc(Long vehicleId);

    @Query("SELECT b FROM VehicleUnavailability b " +
           "WHERE b.vehicle.id = :vehicleId " +
           "AND b.status = :status " +
           "AND b.startTime < :endTime " +
           "AND b.endTime > :startTime " +
           "ORDER BY b.startTime ASC")
    List<VehicleUnavailability> findOverlappingBlocks(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("status") AvailabilityBlockStatus status
    );

    List<VehicleUnavailability> findByVehicleIdAndStatusAndEndTimeAfterOrderByStartTimeAsc(
            Long vehicleId, AvailabilityBlockStatus status, Instant after);

    List<VehicleUnavailability> findByVehicleBusinessIdInAndStatusAndEndTimeAfterOrderByStartTimeAsc(
            Collection<Long> businessIds, AvailabilityBlockStatus status, Instant after);
}

