package com.islandride.geography.repository;

import com.islandride.geography.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByPublicId(String publicId);

    Optional<Location> findByCode(String code);

    List<Location> findByIslandIdAndActiveTrue(Long islandId);

    List<Location> findByIslandIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(Long islandId);

    List<Location> findByActiveTrueAndDeletedAtIsNullOrderByIslandIdAscNameAsc();

    List<Location> findByDeletedAtIsNullOrderByIslandIdAscNameAsc();
}

