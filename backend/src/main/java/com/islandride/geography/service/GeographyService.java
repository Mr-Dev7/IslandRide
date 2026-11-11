package com.islandride.geography.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.domain.State;
import com.islandride.geography.dto.IslandDto;
import com.islandride.geography.dto.LocationDto;
import com.islandride.geography.dto.SaveIslandRequest;
import com.islandride.geography.dto.SaveLocationRequest;
import com.islandride.geography.repository.IslandRepository;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.geography.repository.StateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GeographyService {

    private final IslandRepository islandRepository;
    private final LocationRepository locationRepository;
    private final StateRepository stateRepository;
    private final AuditLogService auditLogService;

    public GeographyService(IslandRepository islandRepository,
                            LocationRepository locationRepository,
                            StateRepository stateRepository,
                            AuditLogService auditLogService) {
        this.islandRepository = islandRepository;
        this.locationRepository = locationRepository;
        this.stateRepository = stateRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<IslandDto> listActiveIslands() {
        return islandRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(GeographyService::toIslandDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IslandDto> listAllIslandsAdmin() {
        return islandRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(GeographyService::toIslandDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> listActiveHubs(Long islandId, String islandCode) {
        Long resolvedIslandId = islandId;
        if (resolvedIslandId == null && islandCode != null && !islandCode.isBlank()) {
            resolvedIslandId = islandRepository.findByCode(islandCode.trim().toUpperCase())
                    .map(Island::getId)
                    .orElseThrow(() -> new ResourceNotFoundException("Island not found for code: " + islandCode));
        }

        List<Location> locations = (resolvedIslandId != null)
                ? locationRepository.findByIslandIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(resolvedIslandId)
                : locationRepository.findByActiveTrueAndDeletedAtIsNullOrderByIslandIdAscNameAsc();
        return locations.stream().map(GeographyService::toLocationDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> listAllLocationsAdmin(Long islandId) {
        List<Location> locations = locationRepository.findByDeletedAtIsNullOrderByIslandIdAscNameAsc();
        return locations.stream()
                .filter(loc -> islandId == null || loc.getIsland().getId().equals(islandId))
                .map(GeographyService::toLocationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Island requireActiveIsland(Long islandId) {
        Island island = islandRepository.findById(islandId)
                .orElseThrow(() -> new ResourceNotFoundException("Island not found: " + islandId));
        if (!island.isActive()) {
            throw new BusinessRuleException("Selected island is currently inactive.");
        }
        return island;
    }

    @Transactional(readOnly = true)
    public Location requireActiveLocationOnIsland(String publicId, Long islandId, String label) {
        Location loc = locationRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(label + " location not found: " + publicId));
        if (loc.getDeletedAt() != null || !loc.isActive()) {
            throw new BusinessRuleException(label + " location is inactive.");
        }
        if (!loc.getIsland().getId().equals(islandId)) {
            throw new BusinessRuleException(label + " location must belong to the vehicle's operating island; inter-island transfer is not permitted.");
        }
        return loc;
    }

    @Transactional
    public IslandDto createIsland(SaveIslandRequest req, Long actorUserId) {
        String code = req.code().trim().toUpperCase();
        if (islandRepository.findByCode(code).isPresent()) {
            throw new ConflictException("An island with code '" + code + "' already exists.");
        }
        State defaultState = stateRepository.findByCode("AN")
                .orElseGet(() -> stateRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new BusinessRuleException("No active state/UT configured in geography.")));

        Island island = new Island();
        island.setState(defaultState);
        island.setName(req.name().trim());
        island.setDisplayAlias(req.displayAlias() != null && !req.displayAlias().isBlank() ? req.displayAlias().trim() : null);
        island.setCode(code);
        island.setDescription(req.description());
        island.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
        island.setActive(req.active() == null || req.active());

        Island saved = islandRepository.save(island);
        auditLogService.record(actorUserId, "ISLAND_CREATED", "Island", saved.getId().toString(), null,
                Map.of("code", saved.getCode(), "name", saved.getName()));
        return toIslandDto(saved);
    }

    @Transactional
    public IslandDto updateIsland(Long id, SaveIslandRequest req, Long actorUserId) {
        Island island = islandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Island not found: " + id));
        String code = req.code().trim().toUpperCase();
        islandRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("An island with code '" + code + "' already exists.");
                });

        island.setName(req.name().trim());
        island.setDisplayAlias(req.displayAlias() != null && !req.displayAlias().isBlank() ? req.displayAlias().trim() : null);
        island.setCode(code);
        island.setDescription(req.description());
        if (req.displayOrder() != null) {
            island.setDisplayOrder(req.displayOrder());
        }
        if (req.active() != null) {
            island.setActive(req.active());
        }

        Island saved = islandRepository.save(island);
        auditLogService.record(actorUserId, "ISLAND_UPDATED", "Island", saved.getId().toString(), null,
                Map.of("code", saved.getCode(), "active", saved.isActive()));
        return toIslandDto(saved);
    }

    @Transactional
    public LocationDto createLocation(SaveLocationRequest req, Long actorUserId) {
        String code = req.code().trim().toUpperCase();
        if (locationRepository.findByCode(code).isPresent()) {
            throw new ConflictException("A location with code '" + code + "' already exists.");
        }
        Island island = islandRepository.findById(req.islandId())
                .orElseThrow(() -> new ResourceNotFoundException("Island not found: " + req.islandId()));

        Location loc = new Location();
        loc.setIsland(island);
        loc.setName(req.name().trim());
        loc.setCode(code);
        loc.setAddress(req.address().trim());
        loc.setLatitude(req.latitude());
        loc.setLongitude(req.longitude());
        loc.setContactPhone(req.contactPhone());
        loc.setActive(req.active() == null || req.active());

        Location saved = locationRepository.save(loc);
        auditLogService.record(actorUserId, "LOCATION_CREATED", "Location", saved.getPublicId(), null,
                Map.of("code", saved.getCode(), "islandId", island.getId()));
        return toLocationDto(saved);
    }

    @Transactional
    public LocationDto updateLocation(String publicId, SaveLocationRequest req, Long actorUserId) {
        Location loc = locationRepository.findByPublicId(publicId)
                .filter(l -> l.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + publicId));

        String code = req.code().trim().toUpperCase();
        locationRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(loc.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("A location with code '" + code + "' already exists.");
                });

        Island island = islandRepository.findById(req.islandId())
                .orElseThrow(() -> new ResourceNotFoundException("Island not found: " + req.islandId()));

        loc.setIsland(island);
        loc.setName(req.name().trim());
        loc.setCode(code);
        loc.setAddress(req.address().trim());
        loc.setLatitude(req.latitude());
        loc.setLongitude(req.longitude());
        loc.setContactPhone(req.contactPhone());
        if (req.active() != null) {
            loc.setActive(req.active());
        }

        Location saved = locationRepository.save(loc);
        auditLogService.record(actorUserId, "LOCATION_UPDATED", "Location", saved.getPublicId(), null,
                Map.of("code", saved.getCode(), "active", saved.isActive()));
        return toLocationDto(saved);
    }

    public static IslandDto toIslandDto(Island island) {
        if (island == null) {
            return null;
        }
        return new IslandDto(
                island.getId(),
                island.getName(),
                island.getDisplayAlias(),
                island.getCode(),
                island.getDescription(),
                island.getDisplayOrder(),
                island.isActive()
        );
    }

    public static LocationDto toLocationDto(Location loc) {
        if (loc == null) {
            return null;
        }
        return new LocationDto(
                loc.getId(),
                loc.getPublicId(),
                loc.getIsland().getId(),
                loc.getIsland().getName(),
                loc.getIsland().getCode(),
                loc.getName(),
                loc.getCode(),
                loc.getAddress(),
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getContactPhone(),
                loc.isActive()
        );
    }
}

