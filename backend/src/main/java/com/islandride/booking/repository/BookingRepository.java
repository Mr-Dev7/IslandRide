package com.islandride.booking.repository;

import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(String publicId);

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByStatusAndApprovalExpiresAtBefore(BookingStatus status, Instant now);

    @Query("SELECT b FROM Booking b " +
           "WHERE b.vehicle.id = :vehicleId " +
           "AND b.status NOT IN :excludedStatuses " +
           "AND b.startTime < :endTime " +
           "AND b.endTime > :startTime")
    List<Booking> findOverlappingBookings(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedStatuses") Collection<BookingStatus> excludedStatuses
    );

    @Query(value = """
            SELECT b.* FROM bookings b
            WHERE b.vehicle_id = :vehicleId
              AND b.status NOT IN (:excludedStatuses)
              AND b.start_time < DATE_ADD(:endTime, INTERVAL :requestBufferMinutes MINUTE)
              AND DATE_ADD(b.end_time, INTERVAL b.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<Booking> findOverlappingBookingsWithBuffer(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("requestBufferMinutes") int requestBufferMinutes,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );

    @Query(value = """
            SELECT DISTINCT b.vehicle_id FROM bookings b
            LEFT JOIN partner_policies pp ON pp.business_id = b.business_id
            WHERE b.status NOT IN (:excludedStatuses)
              AND b.start_time < DATE_ADD(:endTime, INTERVAL COALESCE(pp.turnaround_buffer_minutes, 60) MINUTE)
              AND DATE_ADD(b.end_time, INTERVAL b.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<Long> findConflictingVehicleIdsWithBuffer(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );
}

