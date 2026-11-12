package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
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
public interface RefundRepository extends JpaRepository<Refund, Long>, JpaSpecificationExecutor<Refund> {

    Optional<Refund> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.publicId = :publicId")
    Optional<Refund> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

    List<Refund> findByBookingId(Long bookingId);

    List<Refund> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByStatus(RefundStatus status);

    @Query("SELECT r FROM Refund r WHERE (r.status = com.islandride.paymentfinance.domain.RefundStatus.PENDING " +
           "OR (r.status = com.islandride.paymentfinance.domain.RefundStatus.FAILED AND r.nextRetryAt IS NOT NULL AND r.nextRetryAt <= :now)) " +
           "AND r.retryCount < :maxRetries ORDER BY r.createdAt ASC")
    List<Refund> findRetryEligibleRefunds(@Param("now") Instant now, @Param("maxRetries") int maxRetries);

    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

