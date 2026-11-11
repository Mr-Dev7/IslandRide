package com.islandride.inventory.api;

import com.islandride.availability.dto.VehicleAvailabilityCalendarDto;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.core.dto.PageResponse;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.dto.AdminVehicleDto;
import com.islandride.inventory.dto.VehicleDocumentDto;
import com.islandride.inventory.dto.VehicleModerationRequest;
import com.islandride.inventory.dto.VerifyVehicleDocumentRequest;
import com.islandride.inventory.service.AdminVehicleModerationService;
import com.islandride.inventory.service.VehicleMediaDocumentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
public class AdminVehicleModerationController {

    private final AdminVehicleModerationService moderationService;
    private final VehicleMediaDocumentService mediaDocumentService;
    private final AvailabilityService availabilityService;

    public AdminVehicleModerationController(AdminVehicleModerationService moderationService,
                                            VehicleMediaDocumentService mediaDocumentService,
                                            AvailabilityService availabilityService) {
        this.moderationService = moderationService;
        this.mediaDocumentService = mediaDocumentService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public PageResponse<AdminVehicleDto> listModerationQueue(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) String partnerBusinessPublicId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long islandId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return moderationService.listModerationQueue(status, partnerBusinessPublicId, categoryId, islandId, search, page, size);
    }

    @GetMapping("/{vehiclePublicId}")
    public AdminVehicleDto getVehicleDetail(@PathVariable String vehiclePublicId) {
        return moderationService.getVehicleDetail(vehiclePublicId);
    }

    @PostMapping("/{vehiclePublicId}/approve")
    public AdminVehicleDto approveVehicle(
            @PathVariable String vehiclePublicId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String notes = body != null ? body.get("reason") : null;
        return moderationService.approveVehicle(vehiclePublicId, notes);
    }

    @PostMapping("/{vehiclePublicId}/reject")
    public AdminVehicleDto rejectVehicle(@PathVariable String vehiclePublicId,
                                         @Valid @RequestBody VehicleModerationRequest request) {
        return moderationService.rejectVehicle(vehiclePublicId, request);
    }

    @PostMapping("/{vehiclePublicId}/suspend")
    public AdminVehicleDto suspendVehicle(@PathVariable String vehiclePublicId,
                                          @Valid @RequestBody VehicleModerationRequest request) {
        return moderationService.suspendVehicle(vehiclePublicId, request);
    }

    @PostMapping("/{vehiclePublicId}/unsuspend")
    public AdminVehicleDto unsuspendVehicle(
            @PathVariable String vehiclePublicId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String notes = body != null ? body.get("reason") : null;
        return moderationService.unsuspendVehicle(vehiclePublicId, notes);
    }

    @PatchMapping("/{vehiclePublicId}/documents/{documentPublicId}/verify")
    public VehicleDocumentDto verifyDocument(
            @PathVariable String vehiclePublicId,
            @PathVariable String documentPublicId,
            @Valid @RequestBody VerifyVehicleDocumentRequest request
    ) {
        return moderationService.verifyDocument(vehiclePublicId, documentPublicId, request);
    }

    @GetMapping("/{vehiclePublicId}/images/{imagePublicId}/content")
    public ResponseEntity<byte[]> getAdminImagePreview(@PathVariable String vehiclePublicId,
                                                       @PathVariable String imagePublicId) {
        return MediaResponseHelper.buildMediaResponse(
                mediaDocumentService.loadInternalImageContent(vehiclePublicId, imagePublicId, true),
                false,
                false
        );
    }

    @GetMapping("/{vehiclePublicId}/documents/{documentPublicId}/download")
    public ResponseEntity<byte[]> downloadDocumentForReview(@PathVariable String vehiclePublicId,
                                                            @PathVariable String documentPublicId) {
        return MediaResponseHelper.buildMediaResponse(
                mediaDocumentService.loadPrivateDocumentContent(vehiclePublicId, documentPublicId, true),
                false,
                true
        );
    }

    @GetMapping("/{vehiclePublicId}/availability")
    public VehicleAvailabilityCalendarDto getAdminVehicleAvailability(
            @PathVariable String vehiclePublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return availabilityService.getAdminAvailability(vehiclePublicId, from, to);
    }

    @GetMapping("/documents/expiring")
    public List<VehicleDocumentDto> listAllExpiringDocuments(@RequestParam(defaultValue = "30") int withinDays) {
        return mediaDocumentService.listAdminExpiringDocuments(withinDays);
    }
}

