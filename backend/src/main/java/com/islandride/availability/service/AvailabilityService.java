package com.islandride.availability.service;

import com.islandride.administration.service.AuditLogService;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.availability.dto.AvailabilityBlockDto;
import com.islandride.availability.dto.CreateAvailabilityBlockRequest;
import com.islandride.availability.dto.PublicAvailabilitySlotDto;
import com.islandride.availability.dto.VehicleAvailabilityCalendarDto;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.security.TenantSecurityService;
import com.islandride.inventory.domain.AvailabilityBlockStatus;
import com.islandride.inventory.domain.BlockType;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.domain.VehicleUnavailability;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.partner.domain.Business;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.service.BookingPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AvailabilityService {

    private static final int DEFAULT_CALENDAR_DAYS = 30;
    private static final int MAX_CALENDAR_WINDOW_DAYS = 180;

    private static final Set<BookingStatus> NON_BLOCKING_BOOKING_STATUSES = BookingStatus.NON_BLOCKING_STATUSES;

    private static final List<String> NON_BLOCKING_BOOKING_STATUS_NAMES = BookingStatus.NON_BLOCKING_STATUSES.stream()
            .map(Enum::name)
            .toList();

    private static final Set<BookingStatus> ARCHIVAL_EXCLUDED_BOOKING_STATUSES = BookingStatus.NON_BLOCKING_STATUSES;

    private final VehicleRepository vehicleRepository;
    private final VehicleUnavailabilityRepository unavailabilityRepository;
    private final InventoryHoldRepository holdRepository;
    private final BookingRepository bookingRepository;
    private final TenantSecurityService tenantSecurityService;
    private final AuditLogService auditLogService;
    private final BookingPolicyService bookingPolicyService;

    public AvailabilityService(VehicleRepository vehicleRepository,
                               VehicleUnavailabilityRepository unavailabilityRepository,
                               InventoryHoldRepository holdRepository,
                               BookingRepository bookingRepository,
                               TenantSecurityService tenantSecurityService,
                               AuditLogService auditLogService,
                               BookingPolicyService bookingPolicyService) {
        this.vehicleRepository = vehicleRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.holdRepository = holdRepository;
        this.bookingRepository = bookingRepository;
        this.tenantSecurityService = tenantSecurityService;
        this.auditLogService = auditLogService;
        this.bookingPolicyService = bookingPolicyService;
    }

    @Transactional(readOnly = true)
    public VehicleAvailabilityCalendarDto getPublicAvailability(String vehiclePublicId, Instant from, Instant to) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        if (vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId);
        }
        return buildCalendar(vehicle, from, to, false);
    }

    @Transactional(readOnly = true)
    public VehicleAvailabilityCalendarDto getPartnerAvailability(String vehiclePublicId, Instant from, Instant to) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        tenantSecurityService.requireBusinessReadAccess(vehicle.getBusiness().getId());
        return buildCalendar(vehicle, from, to, true);
    }

    @Transactional(readOnly = true)
    public VehicleAvailabilityCalendarDto getAdminAvailability(String vehiclePublicId, Instant from, Instant to) {
        Vehicle vehicle = requireNonDeletedVehicle(vehiclePublicId);
        return buildCalendar(vehicle, from, to, true);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlockDto> listPartnerUpcomingBlocks(String optionalBusinessPublicId) {
        Business business = tenantSecurityService.resolvePartnerBusinessForRead(optionalBusinessPublicId);
        return unavailabilityRepository
                .findByVehicleBusinessIdInAndStatusAndEndTimeAfterOrderByStartTimeAsc(
                        List.of(business.getId()), AvailabilityBlockStatus.ACTIVE, Instant.now())
                .stream()
                .map(AvailabilityService::toBlockDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isVehicleAvailable(Long vehicleId, Instant startTime, Instant endTime) {
        validateWindow(startTime, endTime);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        Instant now = Instant.now();
        int bufferMinutes = bookingPolicyService.resolveTurnaroundBufferMinutes(vehicle);
        return findBufferedConflictReason(vehicleId, startTime, endTime, bufferMinutes, now) == null;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveBookings(Long vehicleId) {
        List<Booking> active = bookingRepository.findOverlappingBookings(
                vehicleId,
                Instant.EPOCH,
                Instant.now().plus(Duration.ofDays(3650)),
                ARCHIVAL_EXCLUDED_BOOKING_STATUSES
        );
        return !active.isEmpty();
    }

    @Transactional
    public AvailabilityBlockDto createBlock(String vehiclePublicId, CreateAvailabilityBlockRequest request) {
        // Lock the vehicle row to serialize overlapping checks against concurrent blocks/holds/bookings
        Vehicle vehicle = vehicleRepository.findByPublicIdForUpdate(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot create availability blocks on an archived vehicle.");
        }

        validateWindow(request.startTime(), request.endTime());
        Instant now = Instant.now();
        String conflictReason = findFirstConflictReason(vehicle.getId(), request.startTime(), request.endTime(), now);
        if (conflictReason != null) {
            throw new ConflictException(conflictReason);
        }

        BlockType blockType = request.blockType() != null && !request.blockType().isBlank()
                ? BlockType.valueOf(request.blockType())
                : inferBlockType(request.reason());

        VehicleUnavailability block = new VehicleUnavailability();
        block.setVehicle(vehicle);
        block.setBlockType(blockType);
        block.setStartTime(request.startTime());
        block.setEndTime(request.endTime());
        block.setReason(request.reason().trim());
        block.setStatus(AvailabilityBlockStatus.ACTIVE);
        block.setNotes(normalizeBlank(request.notes()));
        block.setRestrictionDetails(normalizeBlank(request.restrictionDetails()));
        block.setRemovable(request.removable() == null || request.removable());
        block.setCreatedBy(tenantSecurityService.getCurrentUserIdOrNull());

        VehicleUnavailability saved = unavailabilityRepository.save(block);
        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "CREATE_AVAILABILITY_BLOCK",
                "VehicleUnavailability",
                saved.getPublicId(),
                null,
                Map.of(
                        "vehiclePublicId", vehicle.getPublicId(),
                        "blockType", saved.getBlockType().name(),
                        "startTime", saved.getStartTime().toString(),
                        "endTime", saved.getEndTime().toString()
                )
        );
        return toBlockDto(saved);
    }

    @Transactional
    public AvailabilityBlockDto removeBlock(String vehiclePublicId, String blockPublicId) {
        Vehicle vehicle = vehicleRepository.findByPublicIdForUpdate(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
        tenantSecurityService.requireBusinessInventoryWriteAccess(vehicle.getBusiness().getId());

        VehicleUnavailability block = unavailabilityRepository.findByPublicId(blockPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Availability block not found: " + blockPublicId));
        if (!block.getVehicle().getId().equals(vehicle.getId())) {
            throw new ResourceNotFoundException("Availability block does not belong to vehicle: " + vehiclePublicId);
        }
        if (block.getStatus() == AvailabilityBlockStatus.CANCELLED) {
            throw new BusinessRuleException("Availability block has already been removed.");
        }
        if (!block.isRemovable()) {
            throw new BusinessRuleException("This availability block is marked non-removable and cannot be deleted directly.");
        }

        block.setStatus(AvailabilityBlockStatus.CANCELLED);
        block.setCancelledBy(tenantSecurityService.getCurrentUserIdOrNull());
        block.setCancelledAt(Instant.now());

        VehicleUnavailability saved = unavailabilityRepository.save(block);
        auditLogService.record(
                tenantSecurityService.getCurrentUserIdOrNull(),
                "REMOVE_AVAILABILITY_BLOCK",
                "VehicleUnavailability",
                saved.getPublicId(),
                Map.of("status", "ACTIVE"),
                Map.of("status", "CANCELLED", "vehiclePublicId", vehicle.getPublicId())
        );
        return toBlockDto(saved);
    }

    private VehicleAvailabilityCalendarDto buildCalendar(Vehicle vehicle, Instant from, Instant to, boolean includeDetailedBlocks) {
        Instant windowStart = from != null ? from : Instant.now();
        Instant windowEnd = to != null ? to : windowStart.plus(Duration.ofDays(DEFAULT_CALENDAR_DAYS));
        validateWindow(windowStart, windowEnd);

        Instant now = Instant.now();
        int bufferMinutes = bookingPolicyService.resolveTurnaroundBufferMinutes(vehicle);
        List<VehicleUnavailability> overlappingBlocks = unavailabilityRepository.findOverlappingBlocks(
                vehicle.getId(), windowStart, windowEnd, AvailabilityBlockStatus.ACTIVE);
        List<InventoryHold> overlappingHolds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                vehicle.getId(), windowStart, windowEnd, bufferMinutes, now);
        List<Booking> overlappingBookings = bookingRepository.findOverlappingBookingsWithBuffer(
                vehicle.getId(), windowStart, windowEnd, bufferMinutes, NON_BLOCKING_BOOKING_STATUS_NAMES);

        List<PublicAvailabilitySlotDto> publicSlots = new ArrayList<>();
        for (VehicleUnavailability block : overlappingBlocks) {
            publicSlots.add(toPublicSlot(block));
        }
        for (InventoryHold hold : overlappingHolds) {
            if (hold.getStartTime().isBefore(windowEnd) && hold.getEndTime().isAfter(windowStart)) {
                publicSlots.add(new PublicAvailabilitySlotDto(hold.getStartTime(), hold.getEndTime(), "HOLD", "Temporarily held"));
            }
            if (hold.getTurnaroundBufferMinutes() > 0) {
                Instant bufferEnd = hold.getEndTime().plus(Duration.ofMinutes(hold.getTurnaroundBufferMinutes()));
                if (hold.getEndTime().isBefore(windowEnd) && bufferEnd.isAfter(windowStart)) {
                    publicSlots.add(new PublicAvailabilitySlotDto(
                            hold.getEndTime(), bufferEnd, "TURNAROUND_BUFFER", "Turnaround & inspection buffer"));
                }
            }
        }
        for (Booking booking : overlappingBookings) {
            if (booking.getStartTime().isBefore(windowEnd) && booking.getEndTime().isAfter(windowStart)) {
                publicSlots.add(new PublicAvailabilitySlotDto(booking.getStartTime(), booking.getEndTime(), "BOOKED", "Reserved"));
            }
            if (booking.getTurnaroundBufferMinutes() > 0) {
                Instant bufferEnd = booking.getEndTime().plus(Duration.ofMinutes(booking.getTurnaroundBufferMinutes()));
                if (booking.getEndTime().isBefore(windowEnd) && bufferEnd.isAfter(windowStart)) {
                    publicSlots.add(new PublicAvailabilitySlotDto(
                            booking.getEndTime(), bufferEnd, "TURNAROUND_BUFFER", "Turnaround & inspection buffer"));
                }
            }
        }
        publicSlots.sort(Comparator.comparing(PublicAvailabilitySlotDto::startTime));

        String conflictReason = findBufferedConflictReason(
                vehicle.getId(),
                windowStart,
                windowEnd,
                bufferMinutes,
                now
        );
        boolean availableForRequestedWindow = conflictReason == null && vehicle.getStatus() == VehicleStatus.ACTIVE;

        List<AvailabilityBlockDto> details = includeDetailedBlocks
                ? overlappingBlocks.stream().map(AvailabilityService::toBlockDto).toList()
                : List.of();

        return new VehicleAvailabilityCalendarDto(
                vehicle.getPublicId(),
                windowStart,
                windowEnd,
                availableForRequestedWindow,
                conflictReason,
                publicSlots,
                details
        );
    }

    private String findFirstConflictReason(Long vehicleId, Instant startTime, Instant endTime, Instant now) {
        List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                vehicleId, startTime, endTime, AvailabilityBlockStatus.ACTIVE);
        List<InventoryHold> holds = holdRepository.findActiveOverlappingHolds(
                vehicleId, startTime, endTime, HoldStatus.HELD, now);
        List<Booking> bookings = bookingRepository.findOverlappingBookings(
                vehicleId, startTime, endTime, NON_BLOCKING_BOOKING_STATUSES);
        return findFirstConflictReason(blocks, holds, bookings, 0);
    }

    private String findBufferedConflictReason(Long vehicleId,
                                             Instant startTime,
                                             Instant endTime,
                                             int bufferMinutes,
                                             Instant now) {
        List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                vehicleId, startTime, endTime, AvailabilityBlockStatus.ACTIVE);
        List<InventoryHold> holds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                vehicleId, startTime, endTime, bufferMinutes, now);
        List<Booking> bookings = bookingRepository.findOverlappingBookingsWithBuffer(
                vehicleId, startTime, endTime, bufferMinutes, NON_BLOCKING_BOOKING_STATUS_NAMES);
        return findFirstConflictReason(blocks, holds, bookings, bufferMinutes);
    }

    private static String findFirstConflictReason(List<VehicleUnavailability> blocks,
                                                  List<InventoryHold> holds,
                                                  List<Booking> bookings,
                                                  int bufferMinutes) {
        if (!bookings.isEmpty()) {
            return bufferMinutes > 0
                    ? "Vehicle already has an overlapping booking or falls within the " + bufferMinutes + "-minute turnaround buffer."
                    : "Vehicle already has an overlapping booking during the requested timeframe.";
        }
        if (!holds.isEmpty()) {
            return bufferMinutes > 0
                    ? "Vehicle has an active temporary inventory hold or falls within its turnaround buffer."
                    : "Vehicle has an active temporary inventory hold during the requested timeframe.";
        }
        if (!blocks.isEmpty()) {
            return "Vehicle already has an overlapping availability or maintenance block during the requested timeframe.";
        }
        return null;
    }

    public static void validateWindow(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessRuleException("Both startTime and endTime must be specified.");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BusinessRuleException("Start time must be strictly before end time.");
        }
        if (Duration.between(startTime, endTime).toDays() > MAX_CALENDAR_WINDOW_DAYS) {
            throw new BusinessRuleException("Requested timeframe cannot exceed " + MAX_CALENDAR_WINDOW_DAYS + " days.");
        }
    }

    public static AvailabilityBlockDto toBlockDto(VehicleUnavailability b) {
        return new AvailabilityBlockDto(
                b.getPublicId(),
                b.getVehicle().getPublicId(),
                b.getBlockType().name(),
                b.getStartTime(),
                b.getEndTime(),
                b.getReason(),
                b.getStatus().name(),
                b.getNotes(),
                b.getRestrictionDetails(),
                b.isRemovable(),
                b.getCreatedAt(),
                b.getCancelledAt()
        );
    }

    private static PublicAvailabilitySlotDto toPublicSlot(VehicleUnavailability b) {
        String slotType = b.getBlockType() != null ? b.getBlockType().name() : "UNAVAILABLE";
        String displayLabel = switch (b.getBlockType()) {
            case MAINTENANCE, DAMAGE_REPAIR -> "Scheduled maintenance";
            case OPERATIONAL_RESTRICTION -> b.getRestrictionDetails() != null && !b.getRestrictionDetails().isBlank()
                    ? b.getRestrictionDetails()
                    : "Operational restriction";
            default -> "Unavailable";
        };
        return new PublicAvailabilitySlotDto(b.getStartTime(), b.getEndTime(), slotType, displayLabel);
    }

    private static BlockType inferBlockType(String reason) {
        if (reason == null) {
            return BlockType.MANUAL_BLOCK;
        }
        String upper = reason.trim().toUpperCase();
        if (upper.contains("MAINT")) {
            return BlockType.MAINTENANCE;
        }
        if (upper.contains("DAMAGE") || upper.contains("REPAIR")) {
            return BlockType.DAMAGE_REPAIR;
        }
        if (upper.contains("RESTRICT")) {
            return BlockType.OPERATIONAL_RESTRICTION;
        }
        return BlockType.MANUAL_BLOCK;
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
}

