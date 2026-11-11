package com.islandride.booking.repository;

import com.islandride.booking.domain.OverbookingExceptionRecord;
import com.islandride.booking.domain.OverbookingExceptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OverbookingExceptionRecordRepository extends JpaRepository<OverbookingExceptionRecord, Long> {

    Optional<OverbookingExceptionRecord> findByPublicId(String publicId);

    Optional<OverbookingExceptionRecord> findByBookingId(Long bookingId);

    List<OverbookingExceptionRecord> findByStatusOrderByDetectedAtDesc(OverbookingExceptionStatus status);
}
