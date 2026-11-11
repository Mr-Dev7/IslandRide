package com.islandride.partner.repository;

import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByPublicId(String publicId);

    Optional<Business> findByOwnerUserId(Long ownerUserId);

    List<Business> findAllByOwnerUserId(Long ownerUserId);

    List<Business> findByVerificationStatus(VerificationStatus status);
}

