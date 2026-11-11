package com.islandride.geography.api;

import com.islandride.core.security.UserPrincipal;
import com.islandride.geography.dto.IslandDto;
import com.islandride.geography.dto.LocationDto;
import com.islandride.geography.dto.SaveIslandRequest;
import com.islandride.geography.dto.SaveLocationRequest;
import com.islandride.geography.service.GeographyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/geography")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
@Tag(name = "Admin Geography", description = "Configure active destination islands and pickup/return locations")
public class AdminGeographyController {

    private final GeographyService geographyService;

    public AdminGeographyController(GeographyService geographyService) {
        this.geographyService = geographyService;
    }

    @GetMapping("/islands")
    @Operation(summary = "List all islands including inactive")
    public ResponseEntity<List<IslandDto>> listAllIslands() {
        return ResponseEntity.ok(geographyService.listAllIslandsAdmin());
    }

    @PostMapping("/islands")
    @Operation(summary = "Create a new island destination")
    public ResponseEntity<IslandDto> createIsland(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody SaveIslandRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(geographyService.createIsland(req, principal.getId()));
    }

    @PutMapping("/islands/{id}")
    @Operation(summary = "Update an existing island")
    public ResponseEntity<IslandDto> updateIsland(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody SaveIslandRequest req) {
        return ResponseEntity.ok(geographyService.updateIsland(id, req, principal.getId()));
    }

    @GetMapping("/locations")
    @Operation(summary = "List all pickup/return hubs")
    public ResponseEntity<List<LocationDto>> listAllLocations(@RequestParam(required = false) Long islandId) {
        return ResponseEntity.ok(geographyService.listAllLocationsAdmin(islandId));
    }

    @PostMapping("/locations")
    @Operation(summary = "Create a pickup/return hub on an island")
    public ResponseEntity<LocationDto> createLocation(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody SaveLocationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(geographyService.createLocation(req, principal.getId()));
    }

    @PutMapping("/locations/{publicId}")
    @Operation(summary = "Update a pickup/return hub")
    public ResponseEntity<LocationDto> updateLocation(@PathVariable String publicId,
                                                      @AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody SaveLocationRequest req) {
        return ResponseEntity.ok(geographyService.updateLocation(publicId, req, principal.getId()));
    }
}

