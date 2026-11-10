package com.islandride.usermanagement.repository;

import com.islandride.usermanagement.domain.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {

    Optional<AdminProfile> findByUserId(Long userId);

    Optional<AdminProfile> findByEmployeeId(String employeeId);
}

