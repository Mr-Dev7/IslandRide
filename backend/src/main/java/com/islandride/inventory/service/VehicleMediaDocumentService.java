package com.islandride.inventory.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.RateLimitService;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.core.storage.MediaStorageService;
import com.islandride.core.storage.MediaVisibility;
import com.islandride.core.storage.StoredMediaObject;
import com.islandride.inventory.domain.DocumentVerificationStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleDocument;
import com.islandride.inventory.domain.VehicleDocumentType;
import com.islandride.inventory.domain.VehicleImage;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.dto.PublicVehicleImageDto;
import com.islandride.inventory.dto.VehicleDocumentDto;
import com.islandride.inventory.dto.VehicleImageDto;
import com.islandride.inventory.repository.VehicleDocumentRepository;
import com.islandride.inventory.repository.VehicleImageRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VehicleMediaDocumentService {

    private static final int EXPIRING_SOON_WINDOW_DAYS = 30;

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository imageRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleDocumentTypeService documentTypeService;
    private final MediaStorageService mediaStorageService;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;
    private final RateLimitService rateLimitService;
    private final int maxImagesPerVehicle;

    public VehicleMediaDocumentService(
            VehicleRepository vehicleRepository,
            VehicleImageRepository imageRepository,
            VehicleDocumentRepository documentRepository,
            VehicleDocumentTypeService documentTypeService,
            MediaStorageService mediaStorageService,
            TenantSecurityService tenantSecurityService,
            AuditLogService auditLogService,
            RateLimitService rateLimitService,
            @Value("${islandride.storage.max-images-per-vehicle:10}") int maxImagesPerVehicle
    ) {
        this.vehicleRepository = vehicleRepository;
        this.imageRepository = imageRepository;
        this.documentRepository = documentRepository;
        this.documentTypeService = documentTypeService;
        this.mediaStorageService = mediaStorageService;
        this.tenantSecurityService = tenantSecurityService;
        this.auditLogService = auditLogService;
        this.rateLimitService = rateLimitService;
        this.maxImagesPerVehicle = maxImagesPerVehicle;
    }

    @Transactional
    public List<VehicleImageDto> uploadImages(String vehiclePublicId, List<MultipartFile> files) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        ensureWritableVehicleState(vehicle);

        if (files == null || files.isEmpty()) {
            throw new BusinessRuleException("At least one vehicle image file is required.");
        }

        enforceUploadRateLimit();

        List<VehicleImage> existing = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId());
        if (existing.size() + files.size() > maxImagesPerVehicle) {
            throw new BusinessRuleException("Maximum " + maxImagesPerVehicle + " images allowed per vehicle.");
        }

        boolean hasPrimary = existing.stream().anyMatch(VehicleImage::isPrimary);
        int nextOrder = existing.stream().mapToInt(VehicleImage::getDisplayOrder).max().orElse(-1) + 1;
        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();

        List<StoredMediaObject> storedObjects = new ArrayList<>();
        List<VehicleImage> created = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                StoredMediaObject stored = mediaStorageService.storeImage("vehicles/" + vehicle.getPublicId(), file);
                storedObjects.add(stored);

                VehicleImage image = new VehicleImage();
                image.setVehicle(vehicle);
                image.setStorageKey(stored.storageKey());
                image.setImageUrl("/api/v1/media/vehicle-images/" + image.getPublicId());
                image.setOriginalFilename(stored.originalFilename());
                image.setContentType(stored.contentType());
                image.setFileSizeBytes(stored.fileSizeBytes());
                image.setPrimary(!hasPrimary && i == 0);
                image.setDisplayOrder(nextOrder + i);
                image.setUploadedBy(actorUserId);
                created.add(image);
            }
            imageRepository.saveAll(created);
        } catch (RuntimeException ex) {
            for (StoredMediaObject stored : storedObjects) {
                mediaStorageService.deleteQuietly(MediaVisibility.PUBLIC, stored.storageKey());
            }
            throw ex;
        }

        auditLogService.record(
                actorUserId,
                "UPLOAD_VEHICLE_IMAGES",
                "Vehicle",
                vehicle.getPublicId(),
                null,
                Map.of("uploadedCount", created.size())
        );

        return imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId())
                .stream()
                .map(VehicleMediaDocumentService::toImageDto)
                .toList();
    }

    @Transactional
    public List<VehicleImageDto> setPrimaryImage(String vehiclePublicId, String imagePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        ensureWritableVehicleState(vehicle);

        List<VehicleImage> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId());
        VehicleImage target = images.stream()
                .filter(img -> img.getPublicId().equals(imagePublicId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle image not found: " + imagePublicId));

        for (VehicleImage img : images) {
            img.setPrimary(img.getId().equals(target.getId()));
        }
        imageRepository.saveAll(images);

        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "SET_PRIMARY_VEHICLE_IMAGE",
                "VehicleImage",
                imagePublicId,
                null,
                Map.of("vehiclePublicId", vehiclePublicId)
        );

        return imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId())
                .stream()
                .map(VehicleMediaDocumentService::toImageDto)
                .toList();
    }

    @Transactional
    public List<VehicleImageDto> reorderImages(String vehiclePublicId, List<String> orderedPublicIds) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        ensureWritableVehicleState(vehicle);

        List<VehicleImage> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId());
        if (orderedPublicIds == null || orderedPublicIds.size() != images.size()) {
            throw new BusinessRuleException("Reorder list must include every image currently attached to the vehicle.");
        }

        Set<String> uniqueIds = new HashSet<>(orderedPublicIds);
        if (uniqueIds.size() != images.size()) {
            throw new BusinessRuleException("Reorder list contains duplicate image IDs.");
        }

        Map<String, VehicleImage> byPublicId = new HashMap<>();
        for (VehicleImage img : images) {
            byPublicId.put(img.getPublicId(), img);
        }

        for (int i = 0; i < orderedPublicIds.size(); i++) {
            String publicId = orderedPublicIds.get(i);
            VehicleImage img = byPublicId.get(publicId);
            if (img == null) {
                throw new ResourceNotFoundException("Image does not belong to vehicle: " + publicId);
            }
            img.setDisplayOrder(i);
        }
        imageRepository.saveAll(images);

        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "REORDER_VEHICLE_IMAGES",
                "Vehicle",
                vehiclePublicId,
                null,
                Map.of("order", orderedPublicIds)
        );

        return imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId())
                .stream()
                .map(VehicleMediaDocumentService::toImageDto)
                .toList();
    }

    @Transactional
    public void deleteImage(String vehiclePublicId, String imagePublicId) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        ensureWritableVehicleState(vehicle);

        VehicleImage target = imageRepository.findByPublicId(imagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle image not found: " + imagePublicId));
        if (!target.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Vehicle image does not belong to vehicle: " + vehiclePublicId);
        }

        boolean wasPrimary = target.isPrimary();
        String storageKey = target.getStorageKey();
        imageRepository.delete(target);

        if (wasPrimary) {
            List<VehicleImage> remaining = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(vehicle.getId());
            if (!remaining.isEmpty()) {
                remaining.getFirst().setPrimary(true);
                imageRepository.save(remaining.getFirst());
            }
        }

        mediaStorageService.deleteQuietly(MediaVisibility.PUBLIC, storageKey);
        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "DELETE_VEHICLE_IMAGE",
                "VehicleImage",
                imagePublicId,
                null,
                Map.of("vehiclePublicId", vehiclePublicId)
        );
    }

    @Transactional(readOnly = true)
    public MediaContentResponse loadPublicImageContent(String imagePublicId) {
        VehicleImage image = imageRepository.findByPublicId(imagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imagePublicId));
        Vehicle vehicle = image.getVehicle();
        if (vehicle.isDeleted()
                || vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new ResourceNotFoundException("Image not found: " + imagePublicId);
        }
        byte[] bytes = mediaStorageService.loadBytes(MediaVisibility.PUBLIC, image.getStorageKey());
        return new MediaContentResponse(bytes, image.getContentType(), image.getOriginalFilename());
    }

    @Transactional(readOnly = true)
    public MediaContentResponse loadInternalImageContent(String vehiclePublicId, String imagePublicId, boolean adminAccess) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        if (!adminAccess) {
            tenantSecurityService.requireBusinessReadAccess(vehicle.getBusiness().getId());
        }
        VehicleImage image = imageRepository.findByPublicId(imagePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imagePublicId));
        if (!image.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Image does not belong to vehicle: " + vehiclePublicId);
        }
        byte[] bytes = mediaStorageService.loadBytes(MediaVisibility.PUBLIC, image.getStorageKey());
        return new MediaContentResponse(bytes, image.getContentType(), image.getOriginalFilename());
    }

    @Transactional
    public VehicleDocumentDto uploadDocument(
            String vehiclePublicId,
            String documentTypeCode,
            String documentNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            MultipartFile file
    ) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());
        ensureWritableVehicleState(vehicle);
        enforceUploadRateLimit();

        VehicleDocumentType docType = documentTypeService.requireActiveByCode(documentTypeCode);
        String applicable = docType.getApplicableVehicleTypes();
        String vehicleTypeName = vehicle.getCategory().getVehicleType().name();
        if (applicable != null && !"ALL".equalsIgnoreCase(applicable) && !applicable.equalsIgnoreCase(vehicleTypeName)) {
            throw new BusinessRuleException("Document type '" + docType.getCode() + "' applies only to " + applicable + " vehicles.");
        }
        if (docType.isRequiresExpiryDate() && expiryDate == null) {
            throw new BusinessRuleException("Expiry date is required for document type '" + docType.getCode() + "'.");
        }
        if (issueDate != null && expiryDate != null && issueDate.isAfter(expiryDate)) {
            throw new BusinessRuleException("Document issue date cannot be after expiry date.");
        }

        StoredMediaObject stored = mediaStorageService.storeDocument("vehicle-docs/" + vehicle.getPublicId(), file);
        Long actorUserId = tenantSecurityService.getCurrentUserIdOrNull();

        VehicleDocument saved;
        try {
            VehicleDocument doc = new VehicleDocument();
            doc.setVehicle(vehicle);
            doc.setDocumentTypeEntity(docType);
            doc.setDocumentType(docType.getCode());
            doc.setDocumentNumber(normalizeBlank(documentNumber));
            doc.setStorageKey(stored.storageKey());
            doc.setDocumentUrl("/api/v1/partners/vehicles/" + vehicle.getPublicId() + "/documents/" + doc.getPublicId() + "/download");
            doc.setOriginalFilename(stored.originalFilename());
            doc.setContentType(stored.contentType());
            doc.setFileSizeBytes(stored.fileSizeBytes());
            doc.setIssueDate(issueDate);
            doc.setExpiryDate(expiryDate);
            boolean alreadyExpired = expiryDate != null && expiryDate.isBefore(LocalDate.now());
            doc.setVerificationStatus(alreadyExpired ? DocumentVerificationStatus.EXPIRED : DocumentVerificationStatus.PENDING);
            doc.setVerified(false);
            doc.setUploadedBy(actorUserId);

            saved = documentRepository.save(doc);
        } catch (RuntimeException ex) {
            mediaStorageService.deleteQuietly(MediaVisibility.PRIVATE, stored.storageKey());
            throw ex;
        }

        auditLogService.record(
                actorUserId,
                "UPLOAD_VEHICLE_DOCUMENT",
                "VehicleDocument",
                saved.getPublicId(),
                null,
                Map.of(
                        "vehiclePublicId", vehicle.getPublicId(),
                        "documentType", saved.getDocumentType(),
                        "expiryDate", expiryDate != null ? expiryDate.toString() : "NONE"
                )
        );

        return toDocumentDto(saved);
    }

    @Transactional(readOnly = true)
    public MediaContentResponse loadPrivateDocumentContent(String vehiclePublicId, String documentPublicId, boolean adminAccess) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        if (!adminAccess) {
            tenantSecurityService.requireBusinessReadAccess(vehicle.getBusiness().getId());
        }
        VehicleDocument doc = documentRepository.findByPublicId(documentPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle document not found: " + documentPublicId));
        if (!doc.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Document does not belong to vehicle: " + vehiclePublicId);
        }

        byte[] bytes = mediaStorageService.loadBytes(MediaVisibility.PRIVATE, doc.getStorageKey());
        return new MediaContentResponse(bytes, doc.getContentType(), doc.getOriginalFilename());
    }

    @Transactional
    public List<VehicleDocumentDto> listPartnerExpiringDocuments(String optionalBusinessPublicId, int withinDays) {
        int clampedDays = Math.max(1, Math.min(withinDays, 365));
        Business business = tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId);
        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        LocalDate threshold = LocalDate.now().plusDays(clampedDays);
        return documentRepository
                .findByVehicleBusinessIdInAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
                        List.of(business.getId()), threshold)
                .stream()
                .map(VehicleMediaDocumentService::toDocumentDto)
                .toList();
    }

    @Transactional
    public List<VehicleDocumentDto> listAdminExpiringDocuments(int withinDays) {
        int clampedDays = Math.max(1, Math.min(withinDays, 365));
        documentRepository.markExpiredDocumentsAsOf(LocalDate.now());
        LocalDate threshold = LocalDate.now().plusDays(clampedDays);
        return documentRepository
                .findByExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(threshold)
                .stream()
                .map(VehicleMediaDocumentService::toDocumentDto)
                .toList();
    }

    public static VehicleImageDto toImageDto(VehicleImage img) {
        return new VehicleImageDto(
                img.getPublicId(),
                img.getImageUrl(),
                img.getOriginalFilename(),
                img.getContentType(),
                img.getFileSizeBytes(),
                img.isPrimary(),
                img.getDisplayOrder(),
                img.getCreatedAt()
        );
    }

    public static PublicVehicleImageDto toPublicImageDto(VehicleImage img) {
        return new PublicVehicleImageDto(
                img.getPublicId(),
                "/api/v1/media/vehicle-images/" + img.getPublicId(),
                img.isPrimary(),
                img.getDisplayOrder()
        );
    }

    public static VehicleDocumentDto toDocumentDto(VehicleDocument doc) {
        LocalDate today = LocalDate.now();
        boolean expired = doc.getExpiryDate() != null && doc.getExpiryDate().isBefore(today);
        boolean expiringSoon = !expired && doc.getExpiryDate() != null
                && !doc.getExpiryDate().isAfter(today.plusDays(EXPIRING_SOON_WINDOW_DAYS));
        DocumentVerificationStatus effectiveStatus = expired
                ? DocumentVerificationStatus.EXPIRED
                : doc.getVerificationStatus();
        String typeName = doc.getDocumentTypeEntity() != null
                ? doc.getDocumentTypeEntity().getName()
                : doc.getDocumentType();

        return new VehicleDocumentDto(
                doc.getPublicId(),
                doc.getVehicle().getPublicId(),
                doc.getDocumentType(),
                typeName,
                doc.getDocumentNumber(),
                "/api/v1/partners/vehicles/" + doc.getVehicle().getPublicId() + "/documents/" + doc.getPublicId() + "/download",
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getIssueDate(),
                doc.getExpiryDate(),
                expired,
                expiringSoon,
                effectiveStatus.name(),
                doc.getRejectionReason(),
                doc.getVerifiedAt(),
                doc.getCreatedAt()
        );
    }

    private void enforceUploadRateLimit() {
        Long userId = tenantSecurityService.getCurrentUserIdOrNull();
        if (userId != null) {
            rateLimitService.checkLimit("upload:user:" + userId, 40, Duration.ofMinutes(5));
        }
    }

    private void ensureWritableVehicleState(Vehicle vehicle) {
        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw new BusinessRuleException("Archived vehicles cannot be modified.");
        }
    }

    private Vehicle requireNonDeletedVehicle(String vehiclePublicId) {
        return vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
    }

    private static String normalizeBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    public record MediaContentResponse(byte[] bytes, String contentType, String filename) {}
}
