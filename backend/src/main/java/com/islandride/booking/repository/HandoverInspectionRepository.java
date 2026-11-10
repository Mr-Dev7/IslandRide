package com.islandride.booking.repository;

import com.islandride.booking.domain.HandoverInspection;
import com.islandride.booking.domain.HandoverType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HandoverInspectionRepository extends JpaRepository<HandoverInspection, Long> {

    Optional<HandoverInspection> findByPublicId(String publicId);

    List<HandoverInspection> findByBookingId(Long bookingId);

    Optional<HandoverInspection> findByBookingIdAndHandoverType(Long bookingId, HandoverType handoverType);
}

