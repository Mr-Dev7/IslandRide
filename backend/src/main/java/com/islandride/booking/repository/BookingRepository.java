package com.islandride.booking.repository;

import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.publicId = :publicId")
    Optional<Booking> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Booking> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    Page<Booking> findByBusinessIdInOrderByCreatedAtDesc(Collection<Long> businessIds, Pageable pageable);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByStatusAndApprovalExpiresAtBefore(BookingStatus status, Instant now);

    List<Booking> findByStatusInAndPaymentDeadlineAtBefore(Collection<BookingStatus> statuses, Instant now);

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
            SELECT b.* FROM bookings b
            WHERE b.vehicle_id = :vehicleId
              AND b.id <> :excludeBookingId
              AND b.status NOT IN (:excludedStatuses)
              AND b.start_time < DATE_ADD(:endTime, INTERVAL :requestBufferMinutes MINUTE)
              AND DATE_ADD(b.end_time, INTERVAL b.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<Booking> findOverlappingBookingsWithBufferExcludingBooking(
            @Param("vehicleId") Long vehicleId,
            @Param("excludeBookingId") Long excludeBookingId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("requestBufferMinutes") int requestBufferMinutes,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );

    @Query(value = """
            SELECT DISTINCT b.vehicle_id FROM bookings b
            JOIN vehicles v ON v.id = b.vehicle_id
            LEFT JOIN partner_policies pp ON pp.business_id = b.business_id
            WHERE b.status NOT IN (:excludedStatuses)
              AND b.start_time < DATE_ADD(:endTime, INTERVAL COALESCE(v.turnaround_buffer_minutes, pp.turnaround_buffer_minutes, :defaultBufferMinutes) MINUTE)
              AND DATE_ADD(b.end_time, INTERVAL b.turnaround_buffer_minutes MINUTE) > :startTime
            """, nativeQuery = true)
    List<Long> findConflictingVehicleIdsWithBuffer(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("defaultBufferMinutes") int defaultBufferMinutes,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );
}

