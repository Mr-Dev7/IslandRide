package com.islandride.availability.repository;

import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryHoldRepository extends JpaRepository<InventoryHold, Long> {

    Optional<InventoryHold> findByPublicId(String publicId);

    List<InventoryHold> findByUserId(Long userId);

    List<InventoryHold> findByVehicleId(Long vehicleId);

    List<InventoryHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant now);

    @Query("SELECT h FROM InventoryHold h " +
           "WHERE h.vehicle.id = :vehicleId " +
           "AND h.status = :status " +
           "AND h.expiresAt > :now " +
           "AND h.startTime < :endTime " +
           "AND h.endTime > :startTime")
    List<InventoryHold> findActiveOverlappingHolds(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("status") HoldStatus status,
            @Param("now") Instant now
    );
}

