package com.islandride.pricing.service;

import com.islandride.core.exception.BusinessRuleException;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.BusinessLocation;
import com.islandride.partner.repository.BusinessLocationRepository;
import com.islandride.pricing.domain.PartnerOperatingHours;
import com.islandride.pricing.repository.PartnerOperatingHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperatingHoursService {

    private final PartnerOperatingHoursRepository operatingHoursRepository;
    private final BusinessLocationRepository businessLocationRepository;
    private final BookingPolicyService bookingPolicyService;
    private final VehicleRepository vehicleRepository;
    private final LocationRepository locationRepository;

    public OperatingHoursService(PartnerOperatingHoursRepository operatingHoursRepository,
                                 BusinessLocationRepository businessLocationRepository,
                                 BookingPolicyService bookingPolicyService,
                                 VehicleRepository vehicleRepository,
                                 LocationRepository locationRepository) {
        this.operatingHoursRepository = operatingHoursRepository;
        this.businessLocationRepository = businessLocationRepository;
        this.bookingPolicyService = bookingPolicyService;
        this.vehicleRepository = vehicleRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public OperatingHoursValidationResult validateLocationsAndOperatingHours(Vehicle vehicle,
                                                                             Location requestedPickup,
                                                                             Location requestedReturn,
                                                                             Instant startTime,
                                                                             Instant endTime) {
        Vehicle managedVehicle = (vehicle != null && vehicle.getId() != null)
                ? vehicleRepository.findById(vehicle.getId()).orElse(vehicle)
                : vehicle;
        if (managedVehicle == null) {
            throw new BusinessRuleException("Vehicle is required to validate operating hours.");
        }

        Location rawPickup = requestedPickup != null ? requestedPickup : managedVehicle.getPickupLocation();
        if (rawPickup == null) {
            throw new BusinessRuleException("Pickup location is required for vehicle " + managedVehicle.getPublicId());
        }
        Location effectivePickup = (rawPickup.getId() != null)
                ? locationRepository.findById(rawPickup.getId()).orElse(rawPickup)
                : rawPickup;

        Location rawReturn = requestedReturn != null
                ? requestedReturn
                : (managedVehicle.getReturnLocation() != null ? managedVehicle.getReturnLocation() : effectivePickup);
        Location effectiveReturn = (rawReturn.getId() != null)
                ? locationRepository.findById(rawReturn.getId()).orElse(rawReturn)
                : rawReturn;

        Long vehicleIslandId = managedVehicle.getIsland().getId();
        if (!effectivePickup.getIsland().getId().equals(vehicleIslandId)
                || !effectiveReturn.getIsland().getId().equals(vehicleIslandId)) {
            throw new BusinessRuleException("Cross-island pickup or return is not allowed for vehicle " + managedVehicle.getPublicId());
        }

        boolean returnMatchesPickup = effectiveReturn.getId().equals(effectivePickup.getId());
        boolean returnMatchesConfiguredReturn = managedVehicle.getReturnLocation() != null
                && effectiveReturn.getId().equals(managedVehicle.getReturnLocation().getId());
        if (!returnMatchesPickup && !returnMatchesConfiguredReturn && !managedVehicle.isAllowDifferentReturnSameIsland()) {
            throw new BusinessRuleException("Vehicle " + managedVehicle.getPublicId() + " does not allow return at a different hub.");
        }

        List<BusinessLocation> partnerHubs = businessLocationRepository.findByBusinessId(managedVehicle.getBusiness().getId());
        if (!partnerHubs.isEmpty()) {
            Set<Long> allowedHubIds = partnerHubs.stream()
                    .map(bl -> bl.getLocation().getId())
                    .collect(Collectors.toSet());
            if (!allowedHubIds.contains(effectivePickup.getId()) && !matchesVehicleDefaultHub(managedVehicle.getPickupLocation(), effectivePickup)) {
                throw new BusinessRuleException("Partner does not operate at pickup location " + effectivePickup.getName());
            }
            if (!allowedHubIds.contains(effectiveReturn.getId()) && !matchesVehicleDefaultHub(managedVehicle.getReturnLocation(), effectiveReturn)) {
                throw new BusinessRuleException("Partner does not operate at return location " + effectiveReturn.getName());
            }
        }

        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(managedVehicle.getBusiness().getId());
        ZoneId zoneId = policy.operatingHoursTimezone();
        validateWindowInstant("pickup", managedVehicle.getBusiness().getId(), effectivePickup, startTime, zoneId, policy);
        validateWindowInstant("return", managedVehicle.getBusiness().getId(), effectiveReturn, endTime, zoneId, policy);

        return new OperatingHoursValidationResult(effectivePickup, effectiveReturn);
    }

    private void validateWindowInstant(String boundaryLabel,
                                       Long businessId,
                                       Location location,
                                       Instant timestamp,
                                       ZoneId zoneId,
                                       EffectiveBookingPolicy policy) {
        ZonedDateTime zoned = timestamp.atZone(zoneId);
        int isoDayOfWeek = zoned.getDayOfWeek().getValue();
        LocalTime localTime = zoned.toLocalTime();

        PartnerOperatingHours resolved = resolveScheduleForDay(businessId, location.getId(), isoDayOfWeek);
        boolean open = resolved != null ? resolved.isOpen() : true;
        LocalTime openTime = resolved != null && resolved.getOpenTime() != null
                ? resolved.getOpenTime()
                : policy.defaultOpenTime();
        LocalTime closeTime = resolved != null && resolved.getCloseTime() != null
                ? resolved.getCloseTime()
                : policy.defaultCloseTime();

        if (!open) {
            throw new BusinessRuleException(
                    "Partner hub '" + location.getName() + "' is closed for " + boundaryLabel
                            + " on " + zoned.getDayOfWeek() + "."
            );
        }
        if (localTime.isBefore(openTime) || localTime.isAfter(closeTime)) {
            throw new BusinessRuleException(
                    "Requested " + boundaryLabel + " time (" + localTime + " " + zoneId + ") is outside operating hours ("
                            + openTime + "-" + closeTime + ") for hub '" + location.getName() + "'."
            );
        }
    }

    private PartnerOperatingHours resolveScheduleForDay(Long businessId, Long locationId, int dayOfWeek) {
        if (locationId != null) {
            Optional<PartnerOperatingHours> locationSpecific = operatingHoursRepository
                    .findByBusinessIdAndScopeKeyAndDayOfWeek(businessId, "LOC_" + locationId, dayOfWeek);
            if (locationSpecific.isPresent()) {
                return locationSpecific.get();
            }
        }
        return operatingHoursRepository
                .findByBusinessIdAndScopeKeyAndDayOfWeek(businessId, PartnerOperatingHours.DEFAULT_SCOPE, dayOfWeek)
                .orElse(null);
    }

    private boolean matchesVehicleDefaultHub(Location vehicleHub, Location requestedHub) {
        return vehicleHub != null && vehicleHub.getId().equals(requestedHub.getId());
    }
}

