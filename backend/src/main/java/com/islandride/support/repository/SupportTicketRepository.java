package com.islandride.support.repository;

import com.islandride.support.domain.SupportTicket;
import com.islandride.support.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByPublicId(String publicId);

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SupportTicket> findByBookingId(Long bookingId);

    List<SupportTicket> findByStatus(TicketStatus status);
}

