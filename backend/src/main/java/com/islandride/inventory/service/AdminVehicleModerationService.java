package com.islandride.inventory.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.core.dto.PageResponse;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.inventory.domain.DocumentVerificationStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleDocument;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.dto.AdminVehicleDto;
import com.islandride.inventory.dto.VehicleDocumentDto;
import com.islandride.inventory.dto.VehicleModerationRequest;
import com.islandride.inventory.dto.VerifyVehicleDocumentRequest;
import com.islandride.inventory.repository.VehicleDocumentRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.VerificationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminVehicleModerationService {

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleMapperSupport mapperSupport;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;

    public AdminVehicleModerationService(VehicleRepository vehicleRepository,
                                         VehicleDocumentRepository documentRepository,
                                         VehicleMapperSupport mapperSupport,
                                         TenantSecurityService tenantSecurityService,
                                         AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.documentRepository = documentRepository;
        this.mapperSupport = mapperSupport;
        this.tenantSecurityService = tenantSecurityService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminVehicleDto> listModerationQueue(
            VehicleStatus status,
            String partnerBusinessPublicId,
            Long categoryId,
            Long islandId,
            String search,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "submittedAt").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Specification<Vehicle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (partnerBusinessPublicId != null && !partnerBusinessPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("business").get("publicId"), partnerBusinessPublicId.trim()));
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
                        cb.like(cb.lower(root.get("registrationNumber")), pattern),
                        cb.like(cb.lower(root.get("business").get("tradeName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Vehicle> result = vehicleRepository.findAll(spec, pageable);
        return PageResponse.from(result, v -> mapperSupport.toAdminDto(v, false));
    }

    @Transactional(readOnly = true)
    public AdminVehicleDto getVehicleDetail(String vehiclePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        return mapperSupport.toAdminDto(vehicle, true);
    }

    @Transactional
    public AdminVehicleDto approveVehicle(String vehiclePublicId, String notes) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        VehicleStatus current = vehicle.getStatus();
        if (current != VehicleStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("Only vehicles in PENDING_REVIEW status can be approved. Current status: " + current);
        }
        if (vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessRuleException("Partner business must be APPROVED before individual vehicles can be approved.");
        }

        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        if (documentRepository.existsByVehicleIdAndExpiryDateIsNotNullAndExpiryDateLessThan(vehicle.getId(), LocalDate.now())) {
            throw new BusinessRuleException("Vehicle has expired compliance documents and cannot be approved until renewed.");
        }

        Long adminUserId = tenantSecurityService.getCurrentUserIdOrNull();
        Instant now = Instant.now();

        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setRejectionReason(null);
        vehicle.setSuspensionReason(null);
        vehicle.setApprovedAt(now);
        vehicle.setModeratedBy(adminUserId);
        vehicle.setModeratedAt(now);
        Vehicle saved = vehicleRepository.save(vehicle);

        String reason = (notes != null && !notes.isBlank()) ? notes.trim() : "Approved by operations admin";
        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.ACTIVE, reason, adminUserId);
        auditLogService.record(
                adminUserId,
                "ADMIN_APPROVE_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                Map.of("status", current.name()),
                Map.of("status", VehicleStatus.ACTIVE.name(), "reason", reason)
        );
        return mapperSupport.toAdminDto(saved, true);
    }

    @Transactional
    public AdminVehicleDto rejectVehicle(String vehiclePublicId, VehicleModerationRequest req) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        VehicleStatus current = vehicle.getStatus();
        if (current != VehicleStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("Only vehicles in PENDING_REVIEW status can be rejected. Current status: " + current);
        }

        Long adminUserId = tenantSecurityService.getCurrentUserIdOrNull();
        Instant now = Instant.now();
        String reason = req.reason().trim();

        vehicle.setStatus(VehicleStatus.REJECTED);
        vehicle.setRejectionReason(reason);
        vehicle.setModeratedBy(adminUserId);
        vehicle.setModeratedAt(now);
        Vehicle saved = vehicleRepository.save(vehicle);

        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.REJECTED, reason, adminUserId);
        auditLogService.record(
                adminUserId,
                "ADMIN_REJECT_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                Map.of("status", current.name()),
                Map.of("status", VehicleStatus.REJECTED.name(), "rejectionReason", reason)
        );
        return mapperSupport.toAdminDto(saved, true);
    }

    @Transactional
    public AdminVehicleDto suspendVehicle(String vehiclePublicId, VehicleModerationRequest req) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        VehicleStatus current = vehicle.getStatus();
        if (current == VehicleStatus.ARCHIVED || current == VehicleStatus.SUSPENDED) {
            throw new BusinessRuleException("Cannot suspend a vehicle in status: " + current);
        }

        Long adminUserId = tenantSecurityService.getCurrentUserIdOrNull();
        Instant now = Instant.now();
        String reason = req.reason().trim();

        vehicle.setStatus(VehicleStatus.SUSPENDED);
        vehicle.setSuspensionReason(reason);
        vehicle.setModeratedBy(adminUserId);
        vehicle.setModeratedAt(now);
        Vehicle saved = vehicleRepository.save(vehicle);

        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.SUSPENDED, reason, adminUserId);
        auditLogService.record(
                adminUserId,
                "ADMIN_SUSPEND_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                Map.of("status", current.name()),
                Map.of("status", VehicleStatus.SUSPENDED.name(), "suspensionReason", reason)
        );
        return mapperSupport.toAdminDto(saved, true);
    }

    @Transactional
    public AdminVehicleDto unsuspendVehicle(String vehiclePublicId, String notes) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        VehicleStatus current = vehicle.getStatus();
        if (current != VehicleStatus.SUSPENDED) {
            throw new BusinessRuleException("Only SUSPENDED vehicles can be unsuspended. Current status: " + current);
        }
        if (vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new BusinessRuleException("Partner business must be APPROVED before unsuspending a vehicle.");
        }

        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        if (documentRepository.existsByVehicleIdAndExpiryDateIsNotNullAndExpiryDateLessThan(vehicle.getId(), LocalDate.now())) {
            throw new BusinessRuleException("Vehicle has expired compliance documents and cannot be reinstated until renewed.");
        }

        Long adminUserId = tenantSecurityService.getCurrentUserIdOrNull();
        Instant now = Instant.now();
        String reason = (notes != null && !notes.isBlank()) ? notes.trim() : "Unsuspended by operations admin";

        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setSuspensionReason(null);
        vehicle.setModeratedBy(adminUserId);
        vehicle.setModeratedAt(now);
        Vehicle saved = vehicleRepository.save(vehicle);

        mapperSupport.recordStatusTransition(saved, current, VehicleStatus.ACTIVE, reason, adminUserId);
        auditLogService.record(
                adminUserId,
                "ADMIN_UNSUSPEND_VEHICLE",
                "Vehicle",
                saved.getPublicId(),
                Map.of("status", current.name()),
                Map.of("status", VehicleStatus.ACTIVE.name(), "reason", reason)
        );
        return mapperSupport.toAdminDto(saved, true);
    }

    @Transactional
    public VehicleDocumentDto verifyDocument(
            String vehiclePublicId,
            String documentPublicId,
            VerifyVehicleDocumentRequest req
    ) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        VehicleDocument doc = documentRepository.findByPublicId(documentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle document not found: " + documentPublicId));
        if (!doc.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Document does not belong to vehicle: " + vehiclePublicId);
        }

        if (req.status() == DocumentVerificationStatus.VERIFIED && doc.isExpiredAsOf(LocalDate.now())) {
            throw new BusinessRuleException("Cannot verify an expired compliance document.");
        }
        if (req.status() == DocumentVerificationStatus.REJECTED
                && (req.rejectionReason() == null || req.rejectionReason().isBlank())) {
            throw new BusinessRuleException("Rejection reason is required when rejecting a compliance document.");
        }
        if (req.status() != DocumentVerificationStatus.VERIFIED
                && req.status() != DocumentVerificationStatus.REJECTED) {
            throw new BusinessRuleException("Moderation status must be VERIFIED or REJECTED.");
        }

        String previousStatus = doc.getVerificationStatus().name();
        Long adminUserId = tenantSecurityService.getCurrentUserIdOrNull();

        doc.setVerificationStatus(req.status());
        doc.setVerified(req.status() == DocumentVerificationStatus.VERIFIED);
        doc.setRejectionReason(req.status() == DocumentVerificationStatus.REJECTED ? req.rejectionReason().trim() : null);
        doc.setVerifiedBy(adminUserId);
        doc.setVerifiedAt(Instant.now());

        VehicleDocument saved = documentRepository.save(doc);
        auditLogService.record(
                adminUserId,
                "ADMIN_MODERATE_VEHICLE_DOCUMENT",
                "VehicleDocument",
                saved.getPublicId(),
                Map.of("verificationStatus", previousStatus),
                Map.of("verificationStatus", saved.getVerificationStatus().name(), "vehiclePublicId", vehicle.getPublicId())
        );

        return VehicleMediaDocumentService.toDocumentDto(saved);
    }

    private Vehicle requireNonDeletedVehicle(String vehiclePublicId) {
        return vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
    }
}

