package com.islandride.inventory.service;

import com.islandride.administration.repository.AuditLogRepository;
import com.islandride.geography.service.GeographyService;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleImage;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.domain.VehicleStatusHistory;
import com.islandride.inventory.dto.*;
import com.islandride.inventory.repository.VehicleDocumentRepository;
import com.islandride.inventory.repository.VehicleImageRepository;
import com.islandride.inventory.repository.VehicleStatusHistoryRepository;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.dto.VehiclePricingOverviewDto;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.VehiclePricingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VehicleMapperSupport {

    private final VehicleImageRepository imageRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleStatusHistoryRepository statusHistoryRepository;
    private final VehiclePricingService pricingService;
    private final BookingPolicyService bookingPolicyService;
    private final AuditLogRepository auditLogRepository;

    public VehicleMapperSupport(VehicleImageRepository imageRepository,
                                VehicleDocumentRepository documentRepository,
                                VehicleStatusHistoryRepository statusHistoryRepository,
                                VehiclePricingService pricingService,
                                BookingPolicyService bookingPolicyService,
                                AuditLogRepository auditLogRepository) {
        this.imageRepository = imageRepository;
        this.documentRepository = documentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.pricingService = pricingService;
        this.bookingPolicyService = bookingPolicyService;
        this.auditLogRepository = auditLogRepository;
    }

    public PartnerVehicleDto toPartnerDto(Vehicle v, boolean includeDetails) {
        List<VehicleImageDto> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(v.getId())
                .stream()
                .map(VehicleMediaDocumentService::toImageDto)
                .toList();

        List<VehicleDocumentDto> documents = includeDetails
                ? documentRepository.findByVehicleIdOrderByCreatedAtDesc(v.getId()).stream()
                        .map(VehicleMediaDocumentService::toDocumentDto)
                        .toList()
                : List.of();

        VehiclePricingOverviewDto pricingOverview = includeDetails ? pricingService.buildOverview(v) : null;
        List<VehicleStatusHistoryDto> history = includeDetails
                ? statusHistoryRepository.findByVehicleIdOrderByCreatedAtDesc(v.getId()).stream()
                        .map(VehicleMapperSupport::toStatusHistoryDto)
                        .toList()
                : List.of();

        return new PartnerVehicleDto(
                v.getPublicId(),
                v.getBusiness().getPublicId(),
                v.getBusiness().getTradeName(),
                v.getBusiness().getVerificationStatus().name(),
                GeographyService.toIslandDto(v.getIsland()),
                v.getPickupLocation() != null ? GeographyService.toLocationDto(v.getPickupLocation()) : null,
                v.getReturnLocation() != null ? GeographyService.toLocationDto(v.getReturnLocation()) : null,
                v.isAllowDifferentReturnSameIsland(),
                VehicleCategoryService.toDto(v.getCategory()),
                v.getTitle(),
                v.getMake(),
                v.getModel(),
                v.getModelYear(),
                v.getDescription(),
                v.getRegistrationNumber(),
                v.getColor(),
                v.getTransmissionType().name(),
                v.getFuelType().name(),
                v.getSeatingCapacity(),
                v.getMileageKmpl(),
                v.getEngineCapacityCc(),
                v.getVehicleCondition().name(),
                v.getRentalInstructions(),
                v.getDailyRate(),
                v.getSecurityDeposit(),
                bookingPolicyService.resolveTurnaroundBufferMinutes(v),
                v.getBookingMode().name(),
                v.getStatus().name(),
                v.getRejectionReason(),
                v.getSuspensionReason(),
                v.getSubmittedAt(),
                v.getApprovedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                new ArrayList<>(v.getFeatures()),
                images,
                documents,
                pricingOverview,
                history
        );
    }

    public AdminVehicleDto toAdminDto(Vehicle v, boolean includeFullAudit) {
        List<VehicleImageDto> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(v.getId())
                .stream()
                .map(VehicleMediaDocumentService::toImageDto)
                .toList();

        List<VehicleDocumentDto> documents = documentRepository.findByVehicleIdOrderByCreatedAtDesc(v.getId()).stream()
                .map(VehicleMediaDocumentService::toDocumentDto)
                .toList();

        VehiclePricingOverviewDto pricingOverview = includeFullAudit ? pricingService.buildOverview(v) : null;
        List<VehicleStatusHistoryDto> history = includeFullAudit
                ? statusHistoryRepository.findByVehicleIdOrderByCreatedAtDesc(v.getId()).stream()
                        .map(VehicleMapperSupport::toStatusHistoryDto)
                        .toList()
                : List.of();
        List<AuditLogEntryDto> auditLogs = includeFullAudit
                ? auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDesc("Vehicle", v.getPublicId()).stream()
                        .map(log -> new AuditLogEntryDto(
                                log.getId(),
                                log.getAction(),
                                log.getEntityName(),
                                log.getEntityId(),
                                log.getActorUser() != null ? log.getActorUser().getId() : null,
                                log.getOldValues(),
                                log.getNewValues(),
                                log.getCreatedAt()
                        ))
                        .toList()
                : List.of();

        return new AdminVehicleDto(
                v.getPublicId(),
                v.getBusiness().getPublicId(),
                v.getBusiness().getTradeName(),
                v.getBusiness().getLegalName(),
                v.getBusiness().getVerificationStatus().name(),
                GeographyService.toIslandDto(v.getIsland()),
                v.getPickupLocation() != null ? GeographyService.toLocationDto(v.getPickupLocation()) : null,
                v.getReturnLocation() != null ? GeographyService.toLocationDto(v.getReturnLocation()) : null,
                v.isAllowDifferentReturnSameIsland(),
                VehicleCategoryService.toDto(v.getCategory()),
                v.getTitle(),
                v.getMake(),
                v.getModel(),
                v.getModelYear(),
                v.getDescription(),
                v.getRegistrationNumber(),
                v.getColor(),
                v.getTransmissionType().name(),
                v.getFuelType().name(),
                v.getSeatingCapacity(),
                v.getMileageKmpl(),
                v.getEngineCapacityCc(),
                v.getVehicleCondition().name(),
                v.getRentalInstructions(),
                v.getDailyRate(),
                v.getSecurityDeposit(),
                bookingPolicyService.resolveTurnaroundBufferMinutes(v),
                v.getBookingMode().name(),
                v.getStatus().name(),
                v.getRejectionReason(),
                v.getSuspensionReason(),
                v.getSubmittedAt(),
                v.getApprovedAt(),
                v.getModeratedBy(),
                v.getModeratedAt(),
                v.getCreatedAt(),
                v.getUpdatedAt(),
                new ArrayList<>(v.getFeatures()),
                images,
                documents,
                pricingOverview,
                history,
                auditLogs
        );
    }

    public PublicVehicleSummaryDto toPublicSummaryDto(Vehicle v, String currency, String primaryImageUrl) {
        return new PublicVehicleSummaryDto(
                v.getPublicId(),
                v.getTitle(),
                v.getMake(),
                v.getModel(),
                v.getModelYear(),
                v.getCategory().getId(),
                v.getCategory().getName(),
                v.getCategory().getCode(),
                v.getCategory().getSlug(),
                v.getCategory().getVehicleType().name(),
                v.getIsland().getId(),
                v.getIsland().getName(),
                v.getIsland().getDisplayAlias(),
                v.getIsland().getCode(),
                v.getPickupLocation() != null ? GeographyService.toLocationDto(v.getPickupLocation()) : null,
                v.getReturnLocation() != null ? GeographyService.toLocationDto(v.getReturnLocation()) : null,
                v.isAllowDifferentReturnSameIsland(),
                v.getTransmissionType().name(),
                v.getFuelType().name(),
                v.getSeatingCapacity(),
                v.getVehicleCondition().name(),
                v.getDailyRate(),
                v.getSecurityDeposit(),
                currency,
                v.getBookingMode().name(),
                v.getBusiness().getPublicId(),
                v.getBusiness().getTradeName(),
                v.getBusiness().getVerificationStatus() == VerificationStatus.APPROVED,
                primaryImageUrl,
                new ArrayList<>(v.getFeatures())
        );
    }

    public PublicVehicleDetailDto toPublicDetailDto(Vehicle v) {
        List<PublicVehicleImageDto> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(v.getId())
                .stream()
                .map(VehicleMediaDocumentService::toPublicImageDto)
                .toList();

        VehiclePricingOverviewDto pricingOverview = pricingService.buildOverview(v);

        return new PublicVehicleDetailDto(
                v.getPublicId(),
                v.getTitle(),
                v.getMake(),
                v.getModel(),
                v.getModelYear(),
                v.getDescription(),
                v.getColor(),
                VehicleCategoryService.toDto(v.getCategory()),
                GeographyService.toIslandDto(v.getIsland()),
                v.getPickupLocation() != null ? GeographyService.toLocationDto(v.getPickupLocation()) : null,
                v.getReturnLocation() != null ? GeographyService.toLocationDto(v.getReturnLocation()) : null,
                v.isAllowDifferentReturnSameIsland(),
                v.getTransmissionType().name(),
                v.getFuelType().name(),
                v.getSeatingCapacity(),
                v.getMileageKmpl(),
                v.getEngineCapacityCc(),
                v.getVehicleCondition().name(),
                v.getRentalInstructions(),
                v.getBookingMode().name(),
                bookingPolicyService.resolveTurnaroundBufferMinutes(v),
                v.getBusiness().getPublicId(),
                v.getBusiness().getTradeName(),
                v.getBusiness().getVerificationStatus() == VerificationStatus.APPROVED,
                new ArrayList<>(v.getFeatures()),
                images,
                pricingOverview.activePlan(),
                pricingOverview.rules()
        );
    }

    public String resolvePrimaryImageUrl(Vehicle v) {
        List<VehicleImage> images = imageRepository.findByVehicleIdOrderByPrimaryDescDisplayOrderAscIdAsc(v.getId());
        if (images.isEmpty()) {
            return null;
        }
        return "/api/v1/media/vehicle-images/" + images.getFirst().getPublicId();
    }

    public void recordStatusTransition(Vehicle vehicle, VehicleStatus from, VehicleStatus to, String reason, Long changedBy) {
        VehicleStatusHistory history = new VehicleStatusHistory();
        history.setVehicle(vehicle);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setChangedBy(changedBy);
        statusHistoryRepository.save(history);
    }

    public static VehicleStatusHistoryDto toStatusHistoryDto(VehicleStatusHistory h) {
        return new VehicleStatusHistoryDto(
                h.getId(),
                h.getFromStatus() != null ? h.getFromStatus().name() : null,
                h.getToStatus().name(),
                h.getReason(),
                h.getChangedBy(),
                h.getCreatedAt()
        );
    }
}

