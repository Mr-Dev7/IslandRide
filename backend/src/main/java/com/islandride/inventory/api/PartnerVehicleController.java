package com.islandride.inventory.api;

import com.islandride.availability.dto.AvailabilityBlockDto;
import com.islandride.availability.dto.CreateAvailabilityBlockRequest;
import com.islandride.availability.dto.VehicleAvailabilityCalendarDto;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.core.dto.PageResponse;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.dto.*;
import com.islandride.inventory.service.PartnerVehicleService;
import com.islandride.inventory.service.VehicleMediaDocumentService;
import com.islandride.pricing.dto.*;
import com.islandride.pricing.service.VehiclePricingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/partners")
public class PartnerVehicleController {

    private final PartnerVehicleService partnerVehicleService;
    private final VehiclePricingService pricingService;
    private final AvailabilityService availabilityService;
    private final VehicleMediaDocumentService mediaDocumentService;

    public PartnerVehicleController(PartnerVehicleService partnerVehicleService,
                                    VehiclePricingService pricingService,
                                    AvailabilityService availabilityService,
                                    VehicleMediaDocumentService mediaDocumentService) {
        this.partnerVehicleService = partnerVehicleService;
        this.pricingService = pricingService;
        this.availabilityService = availabilityService;
        this.mediaDocumentService = mediaDocumentService;
    }

    @GetMapping("/vehicles/summary")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public PartnerInventorySummaryDto getInventorySummary(@RequestParam(required = false) String businessPublicId) {
        return partnerVehicleService.getInventorySummary(businessPublicId);
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public PageResponse<PartnerVehicleDto> listVehicles(
            @RequestParam(required = false) String businessPublicId,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long islandId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return partnerVehicleService.listVehicles(businessPublicId, status, categoryId, islandId, search, page, size);
    }

    @GetMapping("/vehicles/{vehiclePublicId}")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public PartnerVehicleDto getVehicle(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.getVehicle(vehiclePublicId);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto createVehicle(@Valid @RequestBody SaveVehicleRequest request) {
        return partnerVehicleService.createVehicle(request);
    }

    @PutMapping("/vehicles/{vehiclePublicId}")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto updateVehicle(@PathVariable String vehiclePublicId,
                                           @Valid @RequestBody SaveVehicleRequest request) {
        return partnerVehicleService.updateVehicle(vehiclePublicId, request);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/submit")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto submitVehicle(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.submitForReview(vehiclePublicId);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/pause")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto pauseVehicle(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.pauseVehicle(vehiclePublicId);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/resume")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto resumeVehicle(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.resumeVehicle(vehiclePublicId);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/maintenance")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto markMaintenance(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.markMaintenance(vehiclePublicId);
    }

    @DeleteMapping("/vehicles/{vehiclePublicId}")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PartnerVehicleDto archiveVehicle(@PathVariable String vehiclePublicId) {
        return partnerVehicleService.archiveVehicle(vehiclePublicId);
    }

    // ---- Pricing ----

    @GetMapping("/vehicles/{vehiclePublicId}/pricing")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public VehiclePricingOverviewDto getPricing(@PathVariable String vehiclePublicId) {
        return pricingService.getPartnerVehiclePricing(vehiclePublicId);
    }

    @PutMapping("/vehicles/{vehiclePublicId}/pricing")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public VehiclePricingPlanDto savePricing(@PathVariable String vehiclePublicId,
                                             @Valid @RequestBody SaveVehiclePricingRequest request) {
        return pricingService.saveVehiclePricing(vehiclePublicId, request);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/pricing/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public PricingRuleDto addPricingRule(@PathVariable String vehiclePublicId,
                                         @Valid @RequestBody SavePricingRuleRequest request) {
        return pricingService.addPricingRule(vehiclePublicId, request);
    }

    @DeleteMapping("/vehicles/{vehiclePublicId}/pricing/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public void deactivatePricingRule(@PathVariable String vehiclePublicId, @PathVariable Long ruleId) {
        pricingService.deactivatePricingRule(vehiclePublicId, ruleId);
    }

    // ---- Availability Calendar & Blocks ----

    @GetMapping("/vehicles/{vehiclePublicId}/availability")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public VehicleAvailabilityCalendarDto getVehicleAvailability(
            @PathVariable String vehiclePublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return availabilityService.getPartnerAvailability(vehiclePublicId, from, to);
    }

    @PostMapping("/vehicles/{vehiclePublicId}/availability/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public AvailabilityBlockDto createAvailabilityBlock(@PathVariable String vehiclePublicId,
                                                        @Valid @RequestBody CreateAvailabilityBlockRequest request) {
        return availabilityService.createBlock(vehiclePublicId, request);
    }

    @DeleteMapping("/vehicles/{vehiclePublicId}/availability/blocks/{blockPublicId}")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public AvailabilityBlockDto removeAvailabilityBlock(@PathVariable String vehiclePublicId,
                                                        @PathVariable String blockPublicId) {
        return availabilityService.removeBlock(vehiclePublicId, blockPublicId);
    }

    @GetMapping("/calendar/blocks")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public List<AvailabilityBlockDto> listUpcomingBlocks(@RequestParam(required = false) String businessPublicId) {
        return availabilityService.listPartnerUpcomingBlocks(businessPublicId);
    }

    // ---- Vehicle Images ----

    @PostMapping(value = "/vehicles/{vehiclePublicId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public List<VehicleImageDto> uploadImages(@PathVariable String vehiclePublicId,
                                              @RequestPart("files") List<MultipartFile> files) {
        return mediaDocumentService.uploadImages(vehiclePublicId, files);
    }

    @PutMapping("/vehicles/{vehiclePublicId}/images/{imagePublicId}/primary")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public List<VehicleImageDto> setPrimaryImage(@PathVariable String vehiclePublicId,
                                                 @PathVariable String imagePublicId) {
        return mediaDocumentService.setPrimaryImage(vehiclePublicId, imagePublicId);
    }

    @PutMapping("/vehicles/{vehiclePublicId}/images/reorder")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public List<VehicleImageDto> reorderImages(@PathVariable String vehiclePublicId,
                                               @Valid @RequestBody ReorderVehicleImagesRequest request) {
        return mediaDocumentService.reorderImages(vehiclePublicId, request.imagePublicIds());
    }

    @DeleteMapping("/vehicles/{vehiclePublicId}/images/{imagePublicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public void deleteImage(@PathVariable String vehiclePublicId, @PathVariable String imagePublicId) {
        mediaDocumentService.deleteImage(vehiclePublicId, imagePublicId);
    }

    @GetMapping("/vehicles/{vehiclePublicId}/images/{imagePublicId}/content")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public ResponseEntity<byte[]> getPartnerImagePreview(@PathVariable String vehiclePublicId,
                                                         @PathVariable String imagePublicId) {
        return MediaResponseHelper.buildMediaResponse(
                mediaDocumentService.loadInternalImageContent(vehiclePublicId, imagePublicId, false),
                false,
                false
        );
    }

    // ---- Vehicle Compliance Documents ----

    @PostMapping(value = "/vehicles/{vehiclePublicId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public VehicleDocumentDto uploadDocument(
            @PathVariable String vehiclePublicId,
            @RequestParam("documentTypeCode") String documentTypeCode,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam(value = "issueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestPart("file") MultipartFile file
    ) {
        return mediaDocumentService.uploadDocument(vehiclePublicId, documentTypeCode, documentNumber, issueDate, expiryDate, file);
    }

    @GetMapping("/vehicles/{vehiclePublicId}/documents/{documentPublicId}/download")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public ResponseEntity<byte[]> downloadPartnerDocument(@PathVariable String vehiclePublicId,
                                                          @PathVariable String documentPublicId) {
        return MediaResponseHelper.buildMediaResponse(
                mediaDocumentService.loadPrivateDocumentContent(vehiclePublicId, documentPublicId, false),
                false,
                true
        );
    }

    @GetMapping("/documents/expiring")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public List<VehicleDocumentDto> listExpiringDocuments(
            @RequestParam(required = false) String businessPublicId,
            @RequestParam(defaultValue = "30") int withinDays
    ) {
        return mediaDocumentService.listPartnerExpiringDocuments(businessPublicId, withinDays);
    }

    // ---- Partner Hubs Configuration ----

    @GetMapping("/locations")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
    public List<PartnerBusinessLocationDto> listBusinessLocations(@RequestParam(required = false) String businessPublicId) {
        return partnerVehicleService.listBusinessLocations(businessPublicId);
    }

    @PutMapping("/locations")
    @PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER')")
    public List<PartnerBusinessLocationDto> updateBusinessLocations(@Valid @RequestBody UpdatePartnerLocationsRequest request) {
        return partnerVehicleService.updateBusinessLocations(request);
    }
}

