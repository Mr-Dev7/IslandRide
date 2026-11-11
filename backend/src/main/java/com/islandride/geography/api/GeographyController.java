package com.islandride.geography.api;

import com.islandride.geography.dto.IslandDto;
import com.islandride.geography.dto.LocationDto;
import com.islandride.geography.service.GeographyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Geography & Locations", description = "Public database-driven islands and pickup/return hubs")
public class GeographyController {

    private final GeographyService geographyService;

    public GeographyController(GeographyService geographyService) {
        this.geographyService = geographyService;
    }

    @GetMapping("/islands")
    @Operation(summary = "List active Andaman & Nicobar operating islands")
    public ResponseEntity<List<IslandDto>> listIslands() {
        return ResponseEntity.ok(geographyService.listActiveIslands());
    }

    @GetMapping("/hubs")
    @Operation(summary = "List active pickup/return hubs optionally filtered by islandId or islandCode")
    public ResponseEntity<List<LocationDto>> listHubs(
            @RequestParam(required = false) Long islandId,
            @RequestParam(required = false) String islandCode
    ) {
        return ResponseEntity.ok(geographyService.listActiveHubs(islandId, islandCode));
    }
}

