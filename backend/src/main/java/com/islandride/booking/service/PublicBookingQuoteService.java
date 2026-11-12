package com.islandride.booking.service;

import com.islandride.availability.service.AvailabilityService;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.dto.BookingDtos.PricePreviewResponse;
import com.islandride.core.exception.ResourceNotFoundException;
import com.islandride.geography.domain.Location;
import com.islandride.geography.repository.LocationRepository;
import com.islandride.inventory.domain.Vehicle;
import com.islandride.inventory.domain.VehicleStatus;
import com.islandride.inventory.repository.VehicleRepository;
import com.islandride.partner.domain.VerificationStatus;
import com.islandride.pricing.service.AuthoritativePricingService;
import com.islandride.pricing.service.BookingPolicyService;
import com.islandride.pricing.service.EffectiveBookingPolicy;
import com.islandride.pricing.service.OperatingHoursService;
import com.islandride.pricing.service.OperatingHoursValidationResult;
import com.islandride.pricing.service.PricingSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PublicBookingQuoteService {

    private final VehicleRepository vehicleRepository;
    private final LocationRepository locationRepository;
    private final AuthoritativePricingService pricingService;
    private final BookingPolicyService bookingPolicyService;
    private final OperatingHoursService operatingHoursService;
    private final AvailabilityService availabilityService;

    public PublicBookingQuoteService(VehicleRepository vehicleRepository,
                                     LocationRepository locationRepository,
                                     AuthoritativePricingService pricingService,
                                     BookingPolicyService bookingPolicyService,
                                     OperatingHoursService operatingHoursService,
                                     AvailabilityService availabilityService) {
        this.vehicleRepository = vehicleRepository;
        this.locationRepository = locationRepository;
        this.pricingService = pricingService;
        this.bookingPolicyService = bookingPolicyService;
        this.operatingHoursService = operatingHoursService;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public PricePreviewResponse calculatePublicQuote(String vehiclePublicId,
                                                     Instant startTime,
                                                     Instant endTime,
                                                     String pickupLocationPublicId,
                                                     String returnLocationPublicId,
                                                     PaymentMode requestedPaymentMode,
                                                     boolean deliveryRequested,
                                                     Instant quoteAt) {
        Vehicle vehicle = vehicleRepository.findByPublicIdAndDeletedAtIsNull(vehiclePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId));
        if (vehicle.getStatus() != VehicleStatus.ACTIVE
                || vehicle.getBusiness().isDeleted()
                || vehicle.getBusiness().getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehiclePublicId);
        }

        Location pickup = resolveLocation(pickupLocationPublicId);
        Location ret = resolveLocation(returnLocationPublicId);
        OperatingHoursValidationResult validatedLocs = operatingHoursService.validateLocationsAndOperatingHours(
                vehicle, pickup, ret, startTime, endTime
        );

        PricingSnapshot snapshot = pricingService.calculateQuote(
                vehicle,
                startTime,
                endTime,
                requestedPaymentMode != null ? requestedPaymentMode : PaymentMode.FULL,
                deliveryRequested,
                quoteAt != null ? quoteAt : Instant.now()
        );

        EffectiveBookingPolicy policy = bookingPolicyService.resolvePolicy(vehicle);
        var cal = availabilityService.getPublicAvailability(vehiclePublicId, startTime, endTime);

        return new PricePreviewResponse(
                vehicle.getPublicId(),
                vehicle.getTitle(),
                vehicle.getBookingMode() != null ? vehicle.getBookingMode() : BookingMode.INSTANT,
                cal.availableForRequestedWindow(),
                cal.conflictReason(),
                startTime,
                endTime,
                validatedLocs.resolvedPickupLocation().getPublicId(),
                validatedLocs.resolvedPickupLocation().getName(),
                validatedLocs.resolvedReturnLocation().getPublicId(),
                validatedLocs.resolvedReturnLocation().getName(),
                snapshot.currency(),
                snapshot.billingUnit(),
                snapshot.billableDays(),
                snapshot.billableHours(),
                snapshot.dailyRate(),
                snapshot.hourlyRate(),
                snapshot.baseRentalAmount(),
                snapshot.seasonalMultiplier(),
                snapshot.seasonalAdjustmentAmount(),
                snapshot.discountAmount(),
                snapshot.surchargeAmount(),
                snapshot.cleaningFee(),
                snapshot.deliveryFee(),
                snapshot.gstPercentage(),
                snapshot.taxAmount(),
                snapshot.totalPayableAmount(),
                snapshot.securityDepositAmount(),
                snapshot.depositCollectionMethod(),
                snapshot.requestedPaymentMode(),
                snapshot.enforcedPaymentMode(),
                snapshot.advancePercentageApplied(),
                snapshot.advancePaidDueNow(),
                snapshot.pickupBalanceDue(),
                snapshot.turnaroundBufferMinutes(),
                snapshot.fullPaymentCutoffHours(),
                policy.holdDurationSeconds(),
                policy.cancellationDeadlineHours(),
                policy.freeCancellationAllowed(),
                policy.cancellationFeePercentage()
        );
    }

    private Location resolveLocation(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return locationRepository.findByPublicId(publicId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + publicId));
    }
}
