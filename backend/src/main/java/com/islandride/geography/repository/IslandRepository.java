package com.islandride.geography.repository;

import com.islandride.geography.domain.Island;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IslandRepository extends JpaRepository<Island, Long> {

    Optional<Island> findByCode(String code);

    List<Island> findByActiveTrueOrderByDisplayOrderAsc();

    List<Island> findAllByOrderByDisplayOrderAsc();
}

