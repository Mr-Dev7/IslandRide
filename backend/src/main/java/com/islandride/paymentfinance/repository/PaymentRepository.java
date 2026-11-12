package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentStatus;
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
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.publicId = :publicId")
    Optional<Payment> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.gatewayOrderId = :gatewayOrderId")
    Optional<Payment> findByGatewayOrderIdForUpdate(@Param("gatewayOrderId") String gatewayOrderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    List<Payment> findByBookingId(Long bookingId);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

