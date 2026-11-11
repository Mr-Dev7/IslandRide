package com.islandride.inventory.repository;

import com.islandride.inventory.domain.DocumentVerificationStatus;
import com.islandride.inventory.domain.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    Optional<VehicleDocument> findByPublicId(String publicId);

    List<VehicleDocument> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<VehicleDocument> findByVehicleBusinessIdInAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
            Collection<Long> businessIds, LocalDate thresholdDate);

    List<VehicleDocument> findByExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(LocalDate thresholdDate);

    boolean existsByVehicleIdAndExpiryDateIsNotNullAndExpiryDateLessThan(Long vehicleId, LocalDate date);

    boolean existsByVehicleIdAndVerificationStatus(Long vehicleId, DocumentVerificationStatus status);

    @Modifying
    @Query("UPDATE VehicleDocument d SET d.verificationStatus = com.islandride.inventory.domain.DocumentVerificationStatus.EXPIRED, d.verified = false " +
           "WHERE d.expiryDate IS NOT NULL AND d.expiryDate < :today " +
           "AND d.verificationStatus <> com.islandride.inventory.domain.DocumentVerificationStatus.EXPIRED")
    int markExpiredDocumentsAsOf(@Param("today") LocalDate today);
}

