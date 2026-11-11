package com.islandride.booking.repository;

import com.islandride.booking.domain.SecurityDepositRecord;
import com.islandride.booking.domain.SecurityDepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityDepositRecordRepository extends JpaRepository<SecurityDepositRecord, Long> {

    Optional<SecurityDepositRecord> findByPublicId(String publicId);

    Optional<SecurityDepositRecord> findByBookingId(Long bookingId);

    List<SecurityDepositRecord> findByStatus(SecurityDepositStatus status);
}

