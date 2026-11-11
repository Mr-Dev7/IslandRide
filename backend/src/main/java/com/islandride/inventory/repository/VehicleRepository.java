package com.islandride.inventory.repository;

import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByPublicId(String publicId);

    Optional<Vehicle> findByPublicIdAndDeletedAtIsNull(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.publicId = :publicId AND v.deletedAt IS NULL")
    Optional<Vehicle> findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    boolean existsByRegistrationNumberIgnoreCaseAndIdNot(String registrationNumber, Long id);

    List<Vehicle> findByBusinessIdAndDeletedAtIsNull(Long businessId);

    List<Vehicle> findByIslandIdAndStatusAndDeletedAtIsNull(Long islandId, VehicleStatus status);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);

    long countByBusinessIdInAndDeletedAtIsNull(Collection<Long> businessIds);

    long countByBusinessIdInAndDeletedAtIsNullAndStatus(Collection<Long> businessIds, VehicleStatus status);
}

