package com.islandride.booking.api;

import com.islandride.availability.service.InventoryHoldService;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.service.BookingOrchestrationService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tourist")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist Booking & Checkout", description = "Self-service booking lifecycle and checkout for verified tourists")
public class TouristBookingController {

    private final BookingOrchestrationService bookingOrchestrationService;
    private final InventoryHoldService holdService;
    private final PaymentOrchestrationService paymentOrchestrationService;

    public TouristBookingController(BookingOrchestrationService bookingOrchestrationService,
                                    InventoryHoldService holdService,
                                    PaymentOrchestrationService paymentOrchestrationService) {
        this.bookingOrchestrationService = bookingOrchestrationService;
        this.holdService = holdService;
        this.paymentOrchestrationService = paymentOrchestrationService;
    }

    @PostMapping("/holds")
    @Operation(summary = "Acquire a durable inventory hold lock prior to checkout")
    public ResponseEntity<InventoryHoldService.HoldResult> acquireHold(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody BookingDtos.AcquireHoldRequest req) {
        String effectiveKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader.trim()
                : req.idempotencyKey();
        InventoryHoldService.HoldResult result = holdService.acquireHold(new InventoryHoldService.AcquireHoldCommand(
                principal.getId(),
                req.vehiclePublicId(),
                req.startTime(),
                req.endTime(),
                req.purpose(),
                req.pickupLocationPublicId(),
                req.returnLocationPublicId(),
                effectiveKey
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/holds/{holdPublicId}")
    @Operation(summary = "Retrieve an active or recent inventory hold with authoritative quote breakdown")
    public ResponseEntity<BookingDtos.InventoryHoldResponse> getHold(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String holdPublicId) {
        return ResponseEntity.ok(bookingOrchestrationService.getTouristHold(principal.getId(), holdPublicId));
    }

    @DeleteMapping("/holds/{holdPublicId}")
    @Operation(summary = "Release an active inventory hold lock early")
    public ResponseEntity<Void> releaseHold(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String holdPublicId) {
        holdService.releaseHold(holdPublicId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bookings")
    @Operation(summary = "Create an authoritative booking from an active inventory hold")
    public ResponseEntity<BookingOrchestrationService.BookingCreationResult> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody BookingDtos.CreateBookingRequest req) {
        String effectiveKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader.trim()
                : req.idempotencyKey();
        var adjustedReq = new BookingDtos.CreateBookingRequest(
                req.holdPublicId(),
                req.paymentMode(),
                req.deliveryRequested(),
                req.customerDlNumber(),
                req.customerEmergencyContact(),
                req.dlConfirmed(),
                effectiveKey
        );
        BookingOrchestrationService.BookingCreationResult created = bookingOrchestrationService
                .createBookingFromHold(principal.getId(), adjustedReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/bookings")
    @Operation(summary = "List paginated personal bookings for authenticated tourist")
    public ResponseEntity<Page<BookingDtos.BookingSummaryDto>> listMyBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bookingOrchestrationService.listTouristBookings(principal.getId(), pageable));
    }

    @GetMapping("/bookings/{bookingPublicId}")
    @Operation(summary = "Get full booking details and payment history for authenticated tourist")
    public ResponseEntity<BookingDtos.BookingDetailDto> getMyBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId) {
        return ResponseEntity.ok(bookingOrchestrationService.getTouristBooking(principal.getId(), bookingPublicId));
    }

    @PostMapping("/bookings/{bookingPublicId}/payment-order")
    @Operation(summary = "Initiate or retry a gateway checkout order for an approved or pending booking")
    public ResponseEntity<PaymentDtos.GatewayCheckoutOrderResponse> initiateCheckoutOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(paymentOrchestrationService.initiateOrRetryCheckoutOrder(
                bookingPublicId,
                principal.getId(),
                idempotencyKey
        ));
    }

    @PostMapping("/payments/verify")
    @Operation(summary = "Verify client payment signature and confirm booking")
    public ResponseEntity<PaymentDtos.PaymentVerificationResponse> verifyPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentDtos.VerifyPaymentRequest req) {
        return ResponseEntity.ok(paymentOrchestrationService.verifyClientPayment(
                principal.getId(),
                req,
                idempotencyKey
        ));
    }

    @PatchMapping("/bookings/{bookingPublicId}/modify")
    @Operation(summary = "Reschedule or modify an existing booking before the cutoff window")
    public ResponseEntity<BookingDtos.BookingDetailDto> modifyBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingDtos.ModifyBookingRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.modifyBooking(
                principal.getId(),
                bookingPublicId,
                req
        ));
    }

    @GetMapping("/bookings/{bookingPublicId}/cancellation-preview")
    @Operation(summary = "Preview cancellation penalties, refundable amounts and deposit protection")
    public ResponseEntity<BookingDtos.CancellationPreviewResponse> previewCancellation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId) {
        return ResponseEntity.ok(bookingOrchestrationService.previewCancellation(
                principal.getId(),
                bookingPublicId
        ));
    }

    @PostMapping("/bookings/{bookingPublicId}/cancel")
    @Operation(summary = "Execute booking cancellation and process eligible refunds")
    public ResponseEntity<BookingDtos.BookingDetailDto> cancelBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestBody(required = false) BookingDtos.CancelBookingRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.cancelBookingByTourist(
                principal.getId(),
                bookingPublicId,
                req
        ));
    }
}
