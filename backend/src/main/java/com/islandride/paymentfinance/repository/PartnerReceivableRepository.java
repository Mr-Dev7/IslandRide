package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.PartnerReceivable;
import com.islandride.paymentfinance.domain.ReceivableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerReceivableRepository extends JpaRepository<PartnerReceivable, Long> {

    Optional<PartnerReceivable> findByPublicId(String publicId);

    Optional<PartnerReceivable> findByBookingId(Long bookingId);

    List<PartnerReceivable> findByBusinessIdAndStatus(Long businessId, ReceivableStatus status);
}

