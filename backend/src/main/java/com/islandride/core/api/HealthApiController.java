package com.islandride.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Probes", description = "Application and platform operational status endpoints")
public class HealthApiController {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @GetMapping
    @Operation(summary = "Check API Health Status", description = "Returns system operational status, active profile, and UTC timestamp.")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "islandride-backend",
                "version", "1.0.0-SNAPSHOT",
                "environment", activeProfile,
                "timestamp", Instant.now().toString()
        ));
    }
}

