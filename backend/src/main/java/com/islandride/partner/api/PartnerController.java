package com.islandride.partner.api;

import com.islandride.core.security.UserPrincipal;
import com.islandride.partner.dto.BusinessDto;
import com.islandride.partner.dto.PartnerRegisterRequest;
import com.islandride.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partners")
@Tag(name = "Partner Management", description = "Endpoints for vendor onboarding and business profile management")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping("/register")
    @Operation(summary = "Public Partner onboarding registration (creates Owner user + DRAFT business entity)")
    public ResponseEntity<BusinessDto> registerPartner(@Valid @RequestBody PartnerRegisterRequest request) {
        BusinessDto registered = partnerService.registerPartner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    @Operation(summary = "Retrieve current partner business profile and verification status")
    public ResponseEntity<BusinessDto> getMyBusiness(@AuthenticationPrincipal UserPrincipal principal) {
        BusinessDto business = partnerService.getMyBusiness(principal);
        return ResponseEntity.ok(business);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('PARTNER_OWNER')")
    @Operation(summary = "Update partner business profile information")
    public ResponseEntity<BusinessDto> updateMyBusiness(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody BusinessDto updateRequest) {
        BusinessDto updated = partnerService.updateMyBusiness(principal, updateRequest);
        return ResponseEntity.ok(updated);
    }
}
