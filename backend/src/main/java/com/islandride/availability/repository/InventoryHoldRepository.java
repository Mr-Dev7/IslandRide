package com.islandride.availability.repository;

import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query(value = """
            SELECT h.* FROM inventory_holds h
            WHERE h.vehicle_id = :vehicleId
              AND h.status = 'HELD'
              AND h.expires_at > :now
              AND h.start_time < DATE_ADD(:endTime, INTERVAL :requestBufferMinutes MINUTE)
              AND DATE_ADD(h.end_time, INTERVAL h.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<InventoryHold> findActiveOverlappingHoldsWithBuffer(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("requestBufferMinutes") int requestBufferMinutes,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT DISTINCT h.vehicle_id FROM inventory_holds h
            JOIN vehicles v ON v.id = h.vehicle_id
            LEFT JOIN partner_policies pp ON pp.business_id = v.business_id
            WHERE h.status = 'HELD'
              AND h.expires_at > :now
              AND h.start_time < DATE_ADD(:endTime, INTERVAL COALESCE(pp.turnaround_buffer_minutes, 60) MINUTE)
              AND DATE_ADD(h.end_time, INTERVAL h.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<Long> findConflictingHoldVehicleIdsWithBuffer(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("now") Instant now
    );

    Optional<InventoryHold> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying
    @Query("UPDATE InventoryHold h SET h.status = com.islandride.availability.domain.HoldStatus.CONVERTED, h.updatedAt = :now " +
           "WHERE h.id = :holdId AND h.user.id = :userId AND h.status = com.islandride.availability.domain.HoldStatus.HELD " +
           "AND h.expiresAt > :now")
    int markConverted(@Param("holdId") Long holdId, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE InventoryHold h SET h.status = com.islandride.availability.domain.HoldStatus.RELEASED, h.updatedAt = :now " +
           "WHERE h.id = :holdId AND h.user.id = :userId AND h.status = com.islandride.availability.domain.HoldStatus.HELD")
    int markReleased(@Param("holdId") Long holdId, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE InventoryHold h SET h.status = com.islandride.availability.domain.HoldStatus.EXPIRED, h.updatedAt = :now " +
           "WHERE h.status = com.islandride.availability.domain.HoldStatus.HELD AND h.expiresAt <= :now")
    int expireStaleHolds(@Param("now") Instant now);
}

