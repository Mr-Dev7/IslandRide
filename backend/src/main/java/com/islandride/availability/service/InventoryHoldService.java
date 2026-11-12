package com.islandride.availability.service;

import com.islandride.availability.domain.HoldPurpose;
import com.islandride.availability.domain.HoldStatus;
import com.islandride.availability.domain.InventoryHold;
import com.islandride.availability.repository.InventoryHoldRepository;
import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.repository.BookingRepository;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.core.idempotency.IdempotencyService;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.inventory.domain.AvailabilityBlockStatus;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.domain.VehicleUnavailability;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.inventory.repository.VehicleUnavailabilityRepository;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.EffectiveBookingPolicy;
import com.islandride.pricing.service.OperatingHoursService;
import com.islandride.pricing.service.OperatingHoursValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryHoldService {

    private static final Logger log = LoggerFactory.getLogger(InventoryHoldService.class);
    private static final Duration REDIS_LOCK_TTL = Duration.ofSeconds(5);
    private static final int REDIS_LOCK_MAX_ATTEMPTS = 4;
    private static final long REDIS_LOCK_RETRY_MILLIS = 15L;
    private static final List<String> NON_BLOCKING_STATUS_NAMES = BookingStatus.NON_BLOCKING_STATUSES.stream()
            .map(Enum::name)
            .toList();
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final InventoryHoldRepository holdRepository;
    private final BookingRepository bookingRepository;
    private final VehicleUnavailabilityRepository unavailabilityRepository;
    private final BookingPolicyService bookingPolicyService;
    private final OperatingHoursService operatingHoursService;
    private final IdempotencyService idempotencyService;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate txTemplate;

    public InventoryHoldService(VehicleRepository vehicleRepository,
                                UserRepository userRepository,
                                LocationRepository locationRepository,
                                InventoryHoldRepository holdRepository,
                                BookingRepository bookingRepository,
                                VehicleUnavailabilityRepository unavailabilityRepository,
                                BookingPolicyService bookingPolicyService,
                                OperatingHoursService operatingHoursService,
                                IdempotencyService idempotencyService,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                PlatformTransactionManager transactionManager) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.holdRepository = holdRepository;
        this.bookingRepository = bookingRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.bookingPolicyService = bookingPolicyService;
        this.operatingHoursService = operatingHoursService;
        this.idempotencyService = idempotencyService;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public HoldResult acquireHold(AcquireHoldCommand command) {
        if (command == null || command.userId() == null) {
            throw new BusinessRuleException("Authenticated user ID is required to place an inventory hold.");
        }
        AvailabilityService.validateWindow(command.startTime(), command.endTime());

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            return idempotencyService.execute(
                    "INVENTORY_HOLD_ACQUIRE",
                    IdempotencyService.actorKeyForUser(command.userId()),
                    command.idempotencyKey(),
                    command,
                    Duration.ofHours(24),
                    HoldResult.class,
                    () -> {
                        HoldResult result = acquireHoldWithLock(command);
                        return new IdempotencyService.RecordedPayload<>(201, result.publicId(), result);
                    }
            ).body();
        }
        return acquireHoldWithLock(command);
    }

    private HoldResult acquireHoldWithLock(AcquireHoldCommand command) {
        String lockKey = "lock:hold:vehicle:" + command.vehiclePublicId();
        String lockToken = tryAcquireRedisLock(lockKey);
        try {
            return txTemplate.execute(status -> executeAcquireInTx(command));
        } finally {
            releaseRedisLock(lockKey, lockToken);
        }
    }

    private HoldResult executeAcquireInTx(AcquireHoldCommand command) {
        Vehicle vehicle = vehicleRepository.findByPublicIdForUpdate(command.vehiclePublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + command.vehiclePublicId()));
        if (vehicle.getDeletedAt() != null
                || vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED
                || !vehicle.getIsland().isActive()
                || !vehicle.getCategory().isActive()) {
            throw new BusinessRuleException("Vehicle is not eligible for reservation: " + command.vehiclePublicId());
        }

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + command.userId()));

        Instant now = Instant.now();
        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(vehicle);

        long leadMinutes = Duration.between(now, command.startTime()).toMinutes();
        if (leadMinutes < policy.minLeadTimeMinutes()) {
            throw new BusinessRuleException(
                    "Pickup start time must be at least " + policy.minLeadTimeMinutes() + " minutes from now."
            );
        }

        Location requestedPickup = resolveLocation(command.pickupLocationPublicId());
        Location requestedReturn = resolveLocation(command.returnLocationPublicId());
        OperatingHoursValidationResult validatedLocations = operatingHoursService.validateLocationsAndOperatingHours(
                vehicle,
                requestedPickup,
                requestedReturn,
                command.startTime(),
                command.endTime()
        );

        int bufferMinutes = policy.turnaroundBufferMinutes();
        List<Booking> conflictingBookings = bookingRepository.findOverlappingBookingsWithBuffer(
                vehicle.getId(),
                command.startTime(),
                command.endTime(),
                bufferMinutes,
                NON_BLOCKING_STATUS_NAMES
        );
        if (!conflictingBookings.isEmpty()) {
            throw new ConflictException(
                    "Vehicle has an overlapping booking or falls inside the " + bufferMinutes + "-minute turnaround buffer."
            );
        }

        List<InventoryHold> conflictingHolds = holdRepository.findActiveOverlappingHoldsWithBuffer(
                vehicle.getId(),
                command.startTime(),
                command.endTime(),
                bufferMinutes,
                now
        );
        if (!conflictingHolds.isEmpty()) {
            throw new ConflictException(
                    "Vehicle has an active temporary inventory hold or falls inside its turnaround buffer."
            );
        }

        List<VehicleUnavailability> blocks = unavailabilityRepository.findOverlappingBlocks(
                vehicle.getId(),
                command.startTime(),
                command.endTime(),
                AvailabilityBlockStatus.ACTIVE
        );
        if (!blocks.isEmpty()) {
            throw new ConflictException("Vehicle is unavailable due to a maintenance or manual schedule block.");
        }

        int holdSeconds = Math.max(60, policy.holdDurationSeconds());
        InventoryHold hold = new InventoryHold();
        hold.setVehicle(vehicle);
        hold.setUser(user);
        hold.setHoldPurpose(command.purpose() != null ? command.purpose() : HoldPurpose.CHECKOUT);
        hold.setPickupLocation(validatedLocations.resolvedPickupLocation());
        hold.setReturnLocation(validatedLocations.resolvedReturnLocation());
        hold.setStartTime(command.startTime());
        hold.setEndTime(command.endTime());
        hold.setHoldDurationSeconds(holdSeconds);
        hold.setTurnaroundBufferMinutes(bufferMinutes);
        hold.setStatus(HoldStatus.HELD);
        hold.setExpiresAt(now.plusSeconds(holdSeconds));
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            hold.setIdempotencyKey(command.idempotencyKey().trim());
        }

        InventoryHold saved = holdRepository.save(hold);
        return toResult(saved);
    }

    @Transactional
    public void convertHoldToBookingOrFail(Long holdId, Long userId, Instant now) {
        Instant effectiveNow = now != null ? now : Instant.now();
        int updated = holdRepository.markConverted(holdId, userId, effectiveNow);
        if (updated == 0) {
            throw new ConflictException("Inventory hold is expired, already converted, or unavailable for conversion.");
        }
    }

    @Transactional
    public void releaseHold(String holdPublicId, Long userId) {
        InventoryHold hold = holdRepository.findByPublicId(holdPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory hold not found: " + holdPublicId));
        if (!hold.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Inventory hold not found: " + holdPublicId);
        }
        int released = holdRepository.markReleased(hold.getId(), userId, Instant.now());
        if (released == 0 && hold.getStatus() != HoldStatus.RELEASED) {
            throw new ConflictException("Inventory hold in status " + hold.getStatus() + " cannot be released.");
        }
    }

    @Transactional
    public int expireStaleHolds() {
        return holdRepository.expireStaleHolds(Instant.now());
    }

    private Location resolveLocation(String locationPublicId) {
        if (locationPublicId == null || locationPublicId.isBlank()) {
            return null;
        }
        return locationRepository.findByPublicId(locationPublicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationPublicId));
    }

    private String tryAcquireRedisLock(String key) {
        if (redisTemplate == null) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < REDIS_LOCK_MAX_ATTEMPTS; attempt++) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, REDIS_LOCK_TTL);
                if (Boolean.TRUE.equals(acquired)) {
                    return token;
                }
            } catch (Exception ex) {
                log.debug("Redis advisory lock unavailable for key {}, falling back to MySQL row lock: {}", key, ex.getMessage());
                return null;
            }
            if (attempt + 1 < REDIS_LOCK_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(REDIS_LOCK_RETRY_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        // Redis lock is strictly advisory; fall through so MySQL PESSIMISTIC_WRITE handles concurrent serialization
        // without causing false conflicts across non-overlapping date ranges on the same vehicle.
        log.debug("Redis advisory lock busy for key {}, delegating serialization to MySQL PESSIMISTIC_WRITE", key);
        return null;
    }

    private void releaseRedisLock(String key, String token) {
        if (redisTemplate == null || token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (Exception ex) {
            log.debug("Ignoring Redis lock release cleanup error for key {}: {}", key, ex.getMessage());
        }
    }

    private static HoldResult toResult(InventoryHold hold) {
        return new HoldResult(
                hold.getId(),
                hold.getPublicId(),
                hold.getVehicle().getPublicId(),
                hold.getUser().getId(),
                hold.getHoldPurpose(),
                hold.getPickupLocation() != null ? hold.getPickupLocation().getPublicId() : null,
                hold.getReturnLocation() != null ? hold.getReturnLocation().getPublicId() : null,
                hold.getStartTime(),
                hold.getEndTime(),
                hold.getTurnaroundBufferMinutes(),
                hold.getHoldDurationSeconds(),
                hold.getStatus(),
                hold.getExpiresAt()
        );
    }

    public record AcquireHoldCommand(
            Long userId,
            String vehiclePublicId,
            Instant startTime,
            Instant endTime,
            HoldPurpose purpose,
            String pickupLocationPublicId,
            String returnLocationPublicId,
            String idempotencyKey
    ) {
    }

    public record HoldResult(
            Long id,
            String publicId,
            String vehiclePublicId,
            Long userId,
            HoldPurpose purpose,
            String pickupLocationPublicId,
            String returnLocationPublicId,
            Instant startTime,
            Instant endTime,
            int turnaroundBufferMinutes,
            int holdDurationSeconds,
            HoldStatus status,
            Instant expiresAt
    ) {
    }
}
