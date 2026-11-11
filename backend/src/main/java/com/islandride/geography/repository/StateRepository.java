package com.islandride.geography.repository;

import com.islandride.geography.domain.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StateRepository extends JpaRepository<State, Long> {

    Optional<State> findByCode(String code);

    List<State> findByActiveTrueOrderByNameAsc();
}

