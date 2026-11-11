package com.islandride.inventory.api;

import com.islandride.availability.dto.VehicleAvailabilityCalendarDto;
import com.islandride.availability.service.AvailabilityService;
import com.islandride.core.dto.PageResponse;
import com.islandride.inventory.domain.BookingMode;
import com.islandride.inventory.domain.FuelType;
import com.islandride.inventory.domain.TransmissionType;
import com.islandride.inventory.domain.VehicleType;
import com.islandride.inventory.dto.PublicVehicleDetailDto;
import com.islandride.inventory.dto.PublicVehicleSummaryDto;
import com.islandride.inventory.service.PublicCatalogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/vehicles")
public class PublicVehicleCatalogController {

    private final PublicCatalogService catalogService;
    private final AvailabilityService availabilityService;

    public PublicVehicleCatalogController(PublicCatalogService catalogService,
                                          AvailabilityService availabilityService) {
        this.catalogService = catalogService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public PageResponse<PublicVehicleSummaryDto> searchVehicles(
            @RequestParam(required = false) Long islandId,
            @RequestParam(required = false) String islandCode,
            @RequestParam(required = false) String pickupLocationPublicId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) TransmissionType transmissionType,
            @RequestParam(required = false) Integer minSeats,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BookingMode bookingMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return catalogService.searchVehicles(
                islandId, islandCode, pickupLocationPublicId, categoryId, categoryCode,
                vehicleType, brand, q, fuelType, transmissionType, minSeats,
                minPrice, maxPrice, bookingMode, startTime, endTime, sortBy, page, size
        );
    }

    @GetMapping("/{vehiclePublicId}")
    public PublicVehicleDetailDto getVehicleDetail(@PathVariable String vehiclePublicId) {
        return catalogService.getPublicVehicleDetail(vehiclePublicId);
    }

    @GetMapping("/{vehiclePublicId}/availability")
    public VehicleAvailabilityCalendarDto getVehicleAvailability(
            @PathVariable String vehiclePublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return availabilityService.getPublicAvailability(vehiclePublicId, from, to);
    }
}

