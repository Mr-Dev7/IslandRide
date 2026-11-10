package com.islandride.support.repository;

import com.islandride.support.domain.DamageReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DamageReportRepository extends JpaRepository<DamageReport, Long> {

    Optional<DamageReport> findByPublicId(String publicId);

    List<DamageReport> findByBookingId(Long bookingId);

    List<DamageReport> findByVehicleId(Long vehicleId);

    List<DamageReport> findByStatus(String status);
}

