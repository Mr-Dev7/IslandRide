package com.islandride.paymentfinance.repository;

import com.islandride.paymentfinance.domain.OfflineCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfflineCollectionRepository extends JpaRepository<OfflineCollection, Long> {

    List<OfflineCollection> findByBookingId(Long bookingId);

    List<OfflineCollection> findByStaffUserId(Long staffUserId);
}

