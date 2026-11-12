package com.islandride.booking.repository;

import com.islandride.booking.domain.BookingModification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingModificationRepository extends JpaRepository<BookingModification, Long> {

    List<BookingModification> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
