package com.islandride.booking.api;

import com.islandride.booking.domain.PaymentMode;
import com.islandride.booking.dto.BookingDtos.PricePreviewResponse;
import com.islandride.booking.service.PublicBookingQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/public/bookings")
@Tag(name = "Public Booking Quotes", description = "Authoritative server-side booking quotes and pricing preview")
public class PublicBookingQuoteController {

    private final PublicBookingQuoteService quoteService;

    public PublicBookingQuoteController(PublicBookingQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/quote/{vehiclePublicId}")
    @Operation(summary = "Calculate an authoritative booking price quote and policy breakdown")
    public ResponseEntity<PricePreviewResponse> getBookingQuote(
            @PathVariable String vehiclePublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(required = false) String pickupLocationPublicId,
            @RequestParam(required = false) String returnLocationPublicId,
            @RequestParam(required = false) PaymentMode paymentMode,
            @RequestParam(defaultValue = "false") boolean deliveryRequested) {
        PricePreviewResponse quote = quoteService.calculatePublicQuote(
                vehiclePublicId,
                startTime,
                endTime,
                pickupLocationPublicId,
                returnLocationPublicId,
                paymentMode != null ? paymentMode : PaymentMode.FULL,
                deliveryRequested,
                Instant.now()
        );
        return ResponseEntity.ok(quote);
    }
}
