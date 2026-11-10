package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPublicId(String publicId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

    List<Refund> findByBookingId(Long bookingId);

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByStatus(RefundStatus status);
}

