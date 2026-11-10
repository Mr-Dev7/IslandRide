package com.islandride.usermanagement.api;

import com.islandride.core.security.UserPrincipal;
import com.islandride.usermanagement.dto.TouristProfileDto;
import com.islandride.usermanagement.service.TouristProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tourist/profile")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist Profile", description = "Endpoints for tourist KYC and profile management")
public class TouristProfileController {

    private final TouristProfileService touristProfileService;

    public TouristProfileController(TouristProfileService touristProfileService) {
        this.touristProfileService = touristProfileService;
    }

    @GetMapping
    @Operation(summary = "Get current tourist profile details")
    public ResponseEntity<TouristProfileDto> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        TouristProfileDto profile = touristProfileService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @PatchMapping
    @Operation(summary = "Update tourist profile details")
    public ResponseEntity<TouristProfileDto> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestBody TouristProfileDto dto) {
        TouristProfileDto updated = touristProfileService.updateProfile(principal.getId(), dto);
        return ResponseEntity.ok(updated);
    }
}

