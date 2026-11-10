package com.islandride.pricing.repository;

import com.islandride.pricing.domain.SeasonalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SeasonalPriceRepository extends JpaRepository<SeasonalPrice, Long> {

    List<SeasonalPrice> findByIslandIdAndActiveTrue(Long islandId);

    @Query("SELECT s FROM SeasonalPrice s " +
           "WHERE s.island.id = :islandId " +
           "AND s.active = true " +
           "AND s.startDate <= :endDate " +
           "AND s.endDate >= :startDate")
    List<SeasonalPrice> findApplicableSeasons(
            @Param("islandId") Long islandId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

