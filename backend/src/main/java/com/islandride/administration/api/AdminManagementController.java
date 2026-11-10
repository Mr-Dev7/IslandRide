package com.islandride.administration.api;

import com.islandride.administration.service.AdminManagementService;
import com.islandride.identityaccess.domain.UserStatus;
import com.islandride.identityaccess.dto.UserDto;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.partner.dto.BusinessDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration Governance", description = "Endpoints for platform administration, user lifecycle, and partner verification")
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    public AdminManagementController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN', 'SUPPORT_AGENT')")
    @Operation(summary = "List all platform users")
    public ResponseEntity<List<UserDto>> listUsers() {
        return ResponseEntity.ok(adminManagementService.listAllUsers());
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Update user account status (ACTIVE, LOCKED, SUSPENDED, DISABLED)")
    public ResponseEntity<UserDto> updateUserStatus(@PathVariable Long userId,
                                                    @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        UserStatus status = UserStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(adminManagementService.updateUserStatus(userId, status));
    }

    @GetMapping("/partners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "List partner businesses optionally filtered by verification status")
    public ResponseEntity<List<BusinessDto>> listPartners(@RequestParam(required = false) VerificationStatus status) {
        return ResponseEntity.ok(adminManagementService.listBusinessesByStatus(status));
    }

    @PostMapping("/partners/{businessId}/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Approve or reject a partner business KYC and onboarding")
    public ResponseEntity<BusinessDto> verifyPartner(@PathVariable Long businessId,
                                                     @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        String rejectionReason = body.get("rejectionReason");
        VerificationStatus status = VerificationStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(adminManagementService.verifyPartnerBusiness(businessId, status, rejectionReason));
    }
}
