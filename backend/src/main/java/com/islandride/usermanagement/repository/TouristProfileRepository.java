package com.islandride.usermanagement.repository;

import com.islandride.usermanagement.domain.TouristProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TouristProfileRepository extends JpaRepository<TouristProfile, Long> {

    Optional<TouristProfile> findByUserId(Long userId);
}

