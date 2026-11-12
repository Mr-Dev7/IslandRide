package com.islandride.inventory.service;

import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.core.dto.PageResponse;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.inventory.domain.*;
import com.islandride.inventory.dto.PublicVehicleDetailDto;
import com.islandride.inventory.dto.PublicVehicleSummaryDto;
import com.islandride.inventory.repository.VehicleImageRepository;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.VehiclePricingService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class PublicCatalogService {

    private static final List<String> NON_BLOCKING_BOOKING_NAMES = BookingStatus.NON_BLOCKING_STATUSES.stream()
            .map(Enum::name)
            .toList();

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository imageRepository;
    private final VehiclePricingService pricingService;
    private final BookingPolicyService bookingPolicyService;
    private final VehicleMapperSupport mapperSupport;
    private final BookingRepository bookingRepository;
    private final InventoryHoldRepository holdRepository;

    public PublicCatalogService(VehicleRepository vehicleRepository,
                                VehicleImageRepository imageRepository,
                                VehiclePricingService pricingService,
                                BookingPolicyService bookingPolicyService,
                                VehicleMapperSupport mapperSupport,
                                BookingRepository bookingRepository,
                                InventoryHoldRepository holdRepository) {
        this.vehicleRepository = vehicleRepository;
        this.imageRepository = imageRepository;
        this.pricingService = pricingService;
        this.bookingPolicyService = bookingPolicyService;
        this.mapperSupport = mapperSupport;
        this.bookingRepository = bookingRepository;
        this.holdRepository = holdRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicVehicleSummaryDto> searchVehicles(
            Long islandId,
            String islandCode,
            String pickupLocationPublicId,
            Long categoryId,
            String categoryCode,
            VehicleType vehicleType,
            String brand,
            String search,
            FuelType fuelType,
            TransmissionType transmissionType,
            Integer minSeats,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BookingMode bookingMode,
            Instant startTime,
            Instant endTime,
            String sortBy,
            int page,
            int size
    ) {
        if ((startTime != null && endTime == null) || (startTime == null && endTime != null)) {
            throw new BusinessRuleException("Both startTime and endTime are required when filtering by availability dates.");
        }
        if (startTime != null) {
            AvailabilityService.validateWindow(startTime, endTime);
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessRuleException("minPrice cannot exceed maxPrice.");
        }

        Sort sort = resolveSort(sortBy);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50), sort);

        Instant now = Instant.now();
        Set<Long> bufferedConflictIds = new HashSet<>();
        if (startTime != null) {
            int defaultBufferMinutes = bookingPolicyService.resolveDefaultTurnaroundBufferMinutes();
            bufferedConflictIds.addAll(
                    bookingRepository.findConflictingVehicleIdsWithBuffer(startTime, endTime, defaultBufferMinutes, NON_BLOCKING_BOOKING_NAMES)
            );
            bufferedConflictIds.addAll(
                    holdRepository.findConflictingHoldVehicleIdsWithBuffer(startTime, endTime, defaultBufferMinutes, now)
            );
        }

        Specification<Vehicle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), VehicleStatus.ACTIVE));
            predicates.add(cb.isNull(root.get("business").get("deletedAt")));
            predicates.add(cb.equal(root.get("business").get("verificationStatus"), VerificationStatus.APPROVED));
            predicates.add(cb.isTrue(root.get("island").get("active")));
            predicates.add(cb.isTrue(root.get("category").get("active")));

            if (islandId != null) {
                predicates.add(cb.equal(root.get("island").get("id"), islandId));
            }
            if (islandCode != null && !islandCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("island").get("code")), islandCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (pickupLocationPublicId != null && !pickupLocationPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("pickupLocation").get("publicId"), pickupLocationPublicId.trim()));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (categoryCode != null && !categoryCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("category").get("code")), categoryCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("category").get("vehicleType"), vehicleType));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("make")), "%" + brand.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("make")), pattern),
                        cb.like(cb.lower(root.get("model")), pattern),
                        cb.like(cb.lower(root.get("category").get("name")), pattern)
                ));
            }
            if (fuelType != null) {
                predicates.add(cb.equal(root.get("fuelType"), fuelType));
            }
            if (transmissionType != null) {
                predicates.add(cb.equal(root.get("transmissionType"), transmissionType));
            }
            if (minSeats != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("seatingCapacity"), minSeats));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dailyRate"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dailyRate"), maxPrice));
            }
            if (bookingMode != null) {
                predicates.add(cb.equal(root.get("bookingMode"), bookingMode));
            }

            if (startTime != null && query != null) {
                Subquery<Long> blockSub = query.subquery(Long.class);
                Root<VehicleUnavailability> blockRoot = blockSub.from(VehicleUnavailability.class);
                blockSub.select(cb.literal(1L)).where(
                        cb.equal(blockRoot.get("vehicle").get("id"), root.get("id")),
                        cb.equal(blockRoot.get("status"), AvailabilityBlockStatus.ACTIVE),
                        cb.lessThan(blockRoot.get("startTime"), endTime),
                        cb.greaterThan(blockRoot.get("endTime"), startTime)
                );
                predicates.add(cb.not(cb.exists(blockSub)));

                if (!bufferedConflictIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(bufferedConflictIds)));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Vehicle> vehiclePage = vehicleRepository.findAll(spec, pageable);
        List<Long> ids = vehiclePage.getContent().stream().map(Vehicle::getId).toList();
        Map<Long, String> primaryImageByVehicleId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (VehicleImage img : imageRepository.findByVehicleIdInAndPrimaryTrue(ids)) {
                primaryImageByVehicleId.put(
                        img.getVehicle().getId(),
                        "/api/v1/media/vehicle-images/" + img.getPublicId()
                );
            }
        }
        String currency = pricingService.getPlatformCurrency();
        return PageResponse.from(vehiclePage,
                v -> mapperSupport.toPublicSummaryDto(v, currency, primaryImageByVehicleId.get(v.getId())));
    }

    @Transactional(readOnly = true)
    public PublicVehicleDetailDto getPublicVehicleDetail(String vehiclePublicId) {
        Vehicle vehicle = vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
        if (vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED
                || !vehicle.getIsland().isActive()
                || !vehicle.getCategory().isActive()) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId);
        }
        return mapperSupport.toPublicDetailDto(vehicle);
    }

    private static Sort resolveSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "dailyRate").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "dailyRate").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "seats_desc" -> Sort.by(Sort.Direction.DESC, "seatingCapacity").and(Sort.by(Sort.Direction.ASC, "dailyRate"));
            case "year_desc" -> Sort.by(Sort.Direction.DESC, "modelYear").and(Sort.by(Sort.Direction.ASC, "dailyRate"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
