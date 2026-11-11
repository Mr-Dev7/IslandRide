package com.islandride.inventory.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.core.dto.PageResponse;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.geography.domain.Island;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.geography.service.GeographyService;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.dto.*;
import com.islandride.inventory.repository.VehicleDocumentRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.BusinessLocation;
import com.islandride.partner.domain.BusinessLocationId;
import com.islandride.partner.repository.BusinessLocationRepository;
import com.islandride.pricing.domain.VehiclePricingPlan;
import com.islandride.pricing.repository.VehiclePricingPlanRepository;
import com.islandride.pricing.service.VehiclePricingService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class PartnerVehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehiclePricingPlanRepository pricingPlanRepository;
    private final BusinessLocationRepository businessLocationRepository;
    private final LocationRepository locationRepository;
    private final GeographyService geographyService;
    private final VehicleCategoryService categoryService;
    private final VehiclePricingService pricingService;
    private final AvailabilityService availabilityService;
    private final VehicleMapperSupport mapperSupport;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;

    public PartnerVehicleService(VehicleRepository vehicleRepository,
                                 VehicleDocumentRepository documentRepository,
                                 VehiclePricingPlanRepository pricingPlanRepository,
                                 BusinessLocationRepository businessLocationRepository,
                                 LocationRepository locationRepository,
                                 GeographyService geographyService,
                                 VehicleCategoryService categoryService,
                                 VehiclePricingService pricingService,
                                 AvailabilityService availabilityService,
                                 VehicleMapperSupport mapperSupport,
                                 TenantSecurityService tenantSecurityService,
                                 AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.documentRepository = documentRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.businessLocationRepository = businessLocationRepository;
        this.locationRepository = locationRepository;
        this.geographyService = geographyService;
        this.categoryService = categoryService;
        this.pricingService = pricingService;
        this.availabilityService = availabilityService;
        this.mapperSupport = mapperSupport;
        this.tenantSecurityService = tenantSecurityService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PartnerInventorySummaryDto getInventorySummary(String optionalBusinessPublicId) {
        Business business = tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId);
        List<Long> businessIds = List.of(business.getId());

        long total = vehicleRepository.countByBusinessIdInAndDeletedAtIsNull(businessIds);
        long active = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.ACTIVE);
        long pendingReview = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.PENDING_REVIEW);
        long draft = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.DRAFT);
        long pausedOrMaint = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.PAUSED)
                + vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.MAINTENANCE);
        long rejectedOrSuspended = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.REJECTED)
                + vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.SUSPENDED);
        long archived = vehicleRepository.countByBusinessIdInAndDeletedAtIsNullAndStatus(businessIds, VehicleStatus.ARCHIVED);

        long expiringDocs = documentRepository
                .findByVehicleBusinessIdInAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
                        businessIds, LocalDate.now().plusDays(30))
                .size();

        return new PartnerInventorySummaryDto(
                business.getPublicId(),
                business.getTradeName(),
                business.getVerificationStatus().name(),
                total,
                active,
                pendingReview,
                draft,
                pausedOrMaint,
                rejectedOrSuspended,
                archived,
                expiringDocs
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PartnerVehicleDto> listVehicles(
            String optionalBusinessPublicId,
            VehicleStatus status,
            Long categoryId,
            Long islandId,
            String search,
            int page,
            int size
    ) {
        Business business = tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));

        Specification<Vehicle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("business").get("id"), business.getId()));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), VehicleStatus.ARCHIVED));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (islandId != null) {
                predicates.add(cb.equal(root.get("island").get("id"), islandId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("make")), pattern),
                        cb.like(cb.lower(root.get("model")), pattern),
                        cb.like(cb.lower(root.get("registrationNumber")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Vehicle> result = vehicleRepository.findAll(spec, pageable);
        return PageResponse.from(result, v -> mapperSupport.toPartnerDto(v, false));
    }

    @Transactional(readOnly = true)
    public PartnerVehicleDto getVehicle(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessReadAccess(vehicle.getBusiness().getId());
        return mapperSupport.toPartnerDto(vehicle, true);
    }

    @Transactional
    public PartnerVehicleDto createVehicle(SaveVehicleRequest req) {
        Business business = tenantSecurityService.resolvePartnerBusinessForInventoryWrite(req.businessPublicId());
        Island island = geographyService.requireActiveIsland(req.islandId());
        VehicleCategory category = categoryService.requireActiveCategory(req.categoryId());
        Location pickup = resolveOptionalHub(req.pickupLocationPublicId(), island.getId(), "Pickup");
        Location drop = resolveOptionalHub(req.returnLocationPublicId(), island.getId(), "Return");
        boolean allowDiffReturn = Boolean.TRUE.equals(req.allowDifferentReturnSameIsland());
        validateReturnHubRule(pickup, drop, allowDiffReturn);

        String normalizedReg = normalizeRegistration(req.registrationNumber());
        if (vehicleRepository.existsByRegistrationNumberIgnoreCase(normalizedReg)) {
            throw new ConflictException("A vehicle with registration number '" + normalizedReg + "' already exists.");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setBusiness(business);
        applyFields(vehicle, req, island, category, pickup, drop, allowDiffReturn, normalizedReg);
        vehicle.setStatus(VehicleStatus.DRAFT);

        Vehicle saved = vehicleRepository.save(vehicle);

        VehiclePricingPlan initialPlan = new VehiclePricingPlan();
        initialPlan.setVehicle(saved);
        initialPlan.setCurrency(pricingService.getPlatformCurrency());
        initialPlan.setDailyRate(saved.getDailyRate());
        initialPlan.setSecurityDeposit(saved.getSecurityDeposit());
        initialPlan.setActive(true);
        pricingPlanRepository.save(initialPlan);

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        mapperSupport.recordStatusTransition(saved, null, VehicleStatus.DRAFT, "Vehicle draft created", actorUserId);
        auditLogService.record(
                actorUserId,
                "CREATE_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                null,
                Map.of(
                        "businessPublicId", business.getPublicId(),
                        "registrationNumber", saved.getRegistrationNumber(),
                        "status", saved.getStatus().name()
                )
        );

        return mapperSupport.toPartnerDto(saved, true);
    }

    @Transactional
    public PartnerVehicleDto updateVehicle(String vehiclePublicId, SaveVehicleRequest req) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        if (!vehicle.getStatus().isEditableByPartner()) {
            throw new BusinessRuleException("Archived vehicles cannot be edited.");
        }

        Island island = geographyService.requireActiveIsland(req.islandId());
        VehicleCategory category = categoryService.requireActiveCategory(req.categoryId());
        Location pickup = resolveOptionalHub(req.pickupLocationPublicId(), island.getId(), "Pickup");
        Location drop = resolveOptionalHub(req.returnLocationPublicId(), island.getId(), "Return");
        boolean allowDiffReturn = Boolean.TRUE.equals(req.allowDifferentReturnSameIsland());
        validateReturnHubRule(pickup, drop, allowDiffReturn);

        String normalizedReg = normalizeRegistration(req.registrationNumber());
        if (vehicleRepository.existsByRegistrationNumberIgnoreCaseAndIdNot(normalizedReg, vehicle.getId())) {
            throw new ConflictException("Another vehicle with registration number '" + normalizedReg + "' already exists.");
        }

        boolean sensitiveChanged = isSensitiveChange(vehicle, req, island, category, normalizedReg);
        VehicleStatus previousStatus = vehicle.getStatus();

        applyFields(vehicle, req, island, category, pickup, drop, allowDiffReturn, normalizedReg);

        pricingPlanRepository.findFirstByVehicleIdAndActiveTrueOrderByUpdatedAtDesc(vehicle.getId())
                .ifPresent(activePlan -> {
                    activePlan.setDailyRate(vehicle.getDailyRate());
                    activePlan.setSecurityDeposit(vehicle.getSecurityDeposit());
                    pricingPlanRepository.save(activePlan);
                });

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        if (previousStatus == VehicleStatus.ACTIVE && sensitiveChanged) {
            vehicle.setStatus(VehicleStatus.PENDING_REVIEW);
            vehicle.setSubmittedAt(Instant.now());
            vehicle.setApprovedAt(null);
            mapperSupport.recordStatusTransition(
                    vehicle,
                    VehicleStatus.ACTIVE,
                    VehicleStatus.PENDING_REVIEW,
                    "Sensitive specification updated; re-approval required",
                    actorUserId
            );
        }

        Vehicle saved = vehicleRepository.save(vehicle);
        auditLogService.record(
                actorUserId,
                "UPDATE_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                Map.of("status", previousStatus.name()),
                Map.of(
                        "status", saved.getStatus().name(),
                        "sensitiveReReview", (previousStatus == VehicleStatus.ACTIVE && sensitiveChanged),
                        "dailyRate", saved.getDailyRate()
                )
        );

        return mapperSupport.toPartnerDto(saved, true);
    }

    @Transactional
    public PartnerVehicleDto submitForReview(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        VehicleStatus current = vehicle.getStatus();
        if (current != VehicleStatus.DRAFT && current != VehicleStatus.REJECTED) {
            throw new BusinessRuleException("Only DRAFT or REJECTED vehicles can be submitted for review. Current status: " + current);
        }

        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        if (documentRepository.existsByVehicleIdAndExpiryDateIsNotNullAndExpiryDateLessThan(vehicle.getId(), LocalDate.now())) {
            throw new BusinessRuleException("Cannot submit vehicle with expired compliance documents. Please renew expired documents first.");
        }

        vehicle.setStatus(VehicleStatus.PENDING_REVIEW);
        vehicle.setRejectionReason(null);
        vehicle.setSubmittedAt(Instant.now());
        Vehicle saved = vehicleRepository.save(vehicle);

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.PENDING_REVIEW, "Submitted for operations review", actorUserId);
        auditLogService.record(actorUserId, "SUBMIT_VEHICLE_FOR_REVIEW", "Vehicle", saved.getPublicId(),
                Map.of("status", current.name()), Map.of("status", VehicleStatus.PENDING_REVIEW.name()));

        return mapperSupport.toPartnerDto(saved, true);
    }

    @Transactional
    public PartnerVehicleDto pauseVehicle(String vehiclePublicId) {
        return transitionPartnerOperationalState(vehiclePublicId, Set.of(VehicleStatus.ACTIVE), VehicleStatus.PAUSED,
                "Paused by partner", "PAUSE_VEHICLE");
    }

    @Transactional
    public PartnerVehicleDto resumeVehicle(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        VehicleStatus current = vehicle.getStatus();
        if (current != VehicleStatus.PAUSED && current != VehicleStatus.MAINTENANCE) {
            throw new BusinessRuleException("Only PAUSED or MAINTENANCE vehicles can be resumed to ACTIVE. Current status: " + current);
        }

        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        if (documentRepository.existsByVehicleIdAndExpiryDateIsNotNullAndExpiryDateLessThan(vehicle.getId(), LocalDate.now())) {
            throw new BusinessRuleException("Vehicle has expired compliance documents and cannot resume active listing.");
        }

        vehicle.setStatus(VehicleStatus.ACTIVE);
        Vehicle saved = vehicleRepository.save(vehicle);

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.ACTIVE, "Resumed by partner", actorUserId);
        auditLogService.record(actorUserId, "RESUME_VEHICLE", "Vehicle", saved.getPublicId(),
                Map.of("status", current.name()), Map.of("status", VehicleStatus.ACTIVE.name()));
        return mapperSupport.toPartnerDto(saved, true);
    }

    @Transactional
    public PartnerVehicleDto markMaintenance(String vehiclePublicId) {
        return transitionPartnerOperationalState(vehiclePublicId,
                Set.of(VehicleStatus.ACTIVE, VehicleStatus.PAUSED), VehicleStatus.MAINTENANCE,
                "Marked under maintenance by partner", "MARK_VEHICLE_MAINTENANCE");
    }

    @Transactional
    public PartnerVehicleDto archiveVehicle(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        VehicleStatus current = vehicle.getStatus();
        if (current == VehicleStatus.ARCHIVED) {
            throw new BusinessRuleException("Vehicle is already archived.");
        }
        if (availabilityService.hasActiveBookings(vehicle.getId())) {
            throw new ConflictException("Cannot archive vehicle while active or upcoming bookings exist.");
        }

        vehicle.setStatus(VehicleStatus.ARCHIVED);
        Vehicle saved = vehicleRepository.save(vehicle);

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.ARCHIVED, "Archived by partner", actorUserId);
        auditLogService.record(actorUserId, "ARCHIVE_VEHICLE", "Vehicle", saved.getPublicId(),
                Map.of("status", current.name()), Map.of("status", VehicleStatus.ARCHIVED.name()));
        return mapperSupport.toPartnerDto(saved, true);
    }

    @Transactional(readOnly = true)
    public List<PartnerBusinessLocationDto> listBusinessLocations(String optionalBusinessPublicId) {
        Business business = tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId);
        return businessLocationRepository.findByBusinessId(business.getId()).stream()
                .map(bl -> new PartnerBusinessLocationDto(
                        GeographyService.toLocationDto(bl.getLocation()),
                        bl.isPrimary(),
                        bl.isPickupAvailable(),
                        bl.isReturnAvailable()
                ))
                .toList();
    }

    @Transactional
    public List<PartnerBusinessLocationDto> updateBusinessLocations(UpdatePartnerLocationsRequest req) {
        Business business = tenantSecurityService.resolvePartnerBusinessForInventoryWrite(req.businessPublicId());

        long primaryCount = req.locations().stream().filter(UpdatePartnerLocationsRequest.LocationItem::primary).count();
        if (primaryCount > 1) {
            throw new BusinessRuleException("At most one operating location can be marked primary.");
        }

        List<BusinessLocation> updated = new ArrayList<>();
        Set<Long> seenLocIds = new HashSet<>();
        for (UpdatePartnerLocationsRequest.LocationItem item : req.locations()) {
            Location loc = locationRepository.findByPublicId(item.locationPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + item.locationPublicId()));
            if (loc.getDeletedAt() != null || !loc.isActive()) {
                throw new BusinessRuleException("Location '" + loc.getName() + "' is inactive.");
            }
            if (!seenLocIds.add(loc.getId())) {
                throw new BusinessRuleException("Duplicate location in hub configuration: " + loc.getName());
            }

            BusinessLocation bl = new BusinessLocation();
            bl.setId(new BusinessLocationId(business.getId(), loc.getId()));
            bl.setBusiness(business);
            bl.setLocation(loc);
            bl.setPrimary(item.primary());
            bl.setPickupAvailable(item.pickupAvailable());
            bl.setReturnAvailable(item.returnAvailable());
            updated.add(bl);
        }

        businessLocationRepository.deleteByBusinessId(business.getId());
        businessLocationRepository.flush();
        businessLocationRepository.saveAll(updated);

        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "UPDATE_PARTNER_LOCATIONS",
                "Business",
                business.getPublicId(),
                null,
                Map.of("locationCount", updated.size())
        );

        return updated.stream()
                .map(bl -> new PartnerBusinessLocationDto(
                        GeographyService.toLocationDto(bl.getLocation()),
                        bl.isPrimary(),
                        bl.isPickupAvailable(),
                        bl.isReturnAvailable()
                ))
                .toList();
    }

    private PartnerVehicleDto transitionPartnerOperationalState(
            String vehiclePublicId,
            Set<VehicleStatus> allowedFrom,
            VehicleStatus target,
            String reason,
            String auditAction
    ) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        VehicleStatus current = vehicle.getStatus();
        if (!allowedFrom.contains(current)) {
            throw new BusinessRuleException("Cannot transition vehicle from " + current + " to " + target + ".");
        }

        vehicle.setStatus(target);
        Vehicle saved = vehicleRepository.save(vehicle);

        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();
        mapperSupport.recordStatusTransition(saved, current, target, reason, actorUserId);
        auditLogService.record(actorUserId, auditAction, "Vehicle", saved.getPublicId(),
                Map.of("status", current.name()), Map.of("status", target.name()));
        return mapperSupport.toPartnerDto(saved, true);
    }

    private void applyFields(
            Vehicle v,
            SaveVehicleRequest req,
            Island island,
            VehicleCategory category,
            Location pickup,
            Location drop,
            boolean allowDiffReturn,
            String normalizedReg
    ) {
        v.setIsland(island);
        v.setCategory(category);
        v.setPickupLocation(pickup);
        v.setReturnLocation(drop != null ? drop : pickup);
        v.setAllowDifferentReturnSameIsland(allowDiffReturn);
        v.setTitle(req.title().trim());
        v.setMake(req.make().trim());
        v.setModel(req.model().trim());
        v.setModelYear(req.modelYear());
        v.setDescription(normalizeBlank(req.description()));
        v.setRegistrationNumber(normalizedReg);
        v.setColor(normalizeBlank(req.color()));
        v.setTransmissionType(req.transmissionType());
        v.setFuelType(req.fuelType());
        v.setSeatingCapacity(req.seatingCapacity());
        v.setMileageKmpl(req.mileageKmpl());
        v.setEngineCapacityCc(req.engineCapacityCc());
        v.setVehicleCondition(req.vehicleCondition() != null ? req.vehicleCondition() : VehicleCondition.GOOD);
        v.setRentalInstructions(normalizeBlank(req.rentalInstructions()));
        v.setDailyRate(req.dailyRate());
        v.setSecurityDeposit(req.securityDeposit() != null ? req.securityDeposit() : BigDecimal.ZERO);
        v.setTurnaroundBufferMinutes(req.turnaroundBufferMinutes() != null ? req.turnaroundBufferMinutes() : 60);
        v.setBookingMode(req.bookingMode() != null ? req.bookingMode() : BookingMode.INSTANT);

        Set<String> cleanedFeatures = new LinkedHashSet<>();
        if (req.features() != null) {
            for (String f : req.features()) {
                if (f != null && !f.isBlank()) {
                    String trimmed = f.trim();
                    cleanedFeatures.add(trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed);
                }
            }
        }
        v.getFeatures().clear();
        v.getFeatures().addAll(cleanedFeatures);
    }

    private Location resolveOptionalHub(String publicId, Long islandId, String label) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return geographyService.requireActiveLocationOnIsland(publicId.trim(), islandId, label);
    }

    private static void validateReturnHubRule(Location pickup, Location drop, boolean allowDiffReturn) {
        if (!allowDiffReturn && pickup != null && drop != null && !pickup.getId().equals(drop.getId())) {
            throw new BusinessRuleException("Different return hub requires enabling allowDifferentReturnSameIsland.");
        }
    }

    private static boolean isSensitiveChange(
            Vehicle current,
            SaveVehicleRequest req,
            Island newIsland,
            VehicleCategory newCategory,
            String newReg
    ) {
        return !current.getRegistrationNumber().equalsIgnoreCase(newReg)
                || !current.getCategory().getId().equals(newCategory.getId())
                || !current.getIsland().getId().equals(newIsland.getId())
                || !current.getMake().equalsIgnoreCase(req.make().trim())
                || !current.getModel().equalsIgnoreCase(req.model().trim())
                || current.getModelYear() != req.modelYear()
                || current.getFuelType() != req.fuelType()
                || current.getTransmissionType() != req.transmissionType()
                || current.getSeatingCapacity() != req.seatingCapacity();
    }

    private static String normalizeRegistration(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String normalizeBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Vehicle requireNonDeletedVehicle(String vehiclePublicId) {
        return vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
    }
}

