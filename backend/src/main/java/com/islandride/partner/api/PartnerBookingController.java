package com.islandride.partner.api;

import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.service.BookingOrchestrationService;
import com.islandride.core.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partners/bookings")
@PreAuthorize("hasAnyRole('PARTNER_OWNER', 'PARTNER_MANAGER', 'PARTNER_STAFF')")
@Tag(name = "Partner Fleet Bookings", description = "Operational management of fleet bookings, approvals, handovers and no-shows")
public class PartnerBookingController {

    private final BookingOrchestrationService bookingOrchestrationService;

    public PartnerBookingController(BookingOrchestrationService bookingOrchestrationService) {
        this.bookingOrchestrationService = bookingOrchestrationService;
    }

    @GetMapping
    @Operation(summary = "List paginated fleet bookings for partner businesses")
    public ResponseEntity<Page<BookingDtos.BookingSummaryDto>> listPartnerBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String businessPublicId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bookingOrchestrationService.listPartnerBookings(principal, businessPublicId, pageable));
    }

    @GetMapping("/{bookingPublicId}")
    @Operation(summary = "Get detailed booking record, inspections, deposits and status history")
    public ResponseEntity<BookingDtos.BookingDetailDto> getPartnerBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId) {
        return ResponseEntity.ok(bookingOrchestrationService.getPartnerBooking(principal, bookingPublicId));
    }

    @PostMapping("/{bookingPublicId}/approve")
    @Operation(summary = "Approve an APPROVAL-mode reservation request")
    public ResponseEntity<BookingDtos.BookingDetailDto> approveBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestBody(required = false) BookingDtos.PartnerApprovalDecisionRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.partnerApproveBooking(principal.getId(), bookingPublicId, req));
    }

    @PostMapping("/{bookingPublicId}/reject")
    @Operation(summary = "Reject an APPROVAL-mode reservation request")
    public ResponseEntity<BookingDtos.BookingDetailDto> rejectBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestBody(required = false) BookingDtos.PartnerApprovalDecisionRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.partnerRejectBooking(principal.getId(), bookingPublicId, req));
    }

    @PostMapping("/{bookingPublicId}/handover/pickup")
    @Operation(summary = "Record physical pickup handover, checklist, odometer, fuel, balance & deposit collection")
    public ResponseEntity<BookingDtos.BookingDetailDto> recordPickupHandover(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingDtos.PickupHandoverRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.recordPickupHandover(principal.getId(), bookingPublicId, req));
    }

    @PostMapping("/{bookingPublicId}/handover/return")
    @Operation(summary = "Record vehicle return inspection, checklist, odometer, fuel, deposit disposition and complete rental")
    public ResponseEntity<BookingDtos.BookingDetailDto> recordReturnComplete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingDtos.ReturnCompleteRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.recordReturnCompletion(principal.getId(), bookingPublicId, req));
    }

    @PostMapping("/{bookingPublicId}/no-show")
    @Operation(summary = "Claim tourist NO_SHOW after the required grace window with evidence notes/reference")
    public ResponseEntity<BookingDtos.BookingDetailDto> markNoShow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestBody(required = false) BookingDtos.NoShowRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.markNoShow(principal.getId(), bookingPublicId, req));
    }

    @PostMapping("/{bookingPublicId}/deposit/settle")
    @Operation(summary = "Settle, release or forfeit security deposit during inspection window")
    public ResponseEntity<BookingDtos.BookingDetailDto> settleDeposit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestBody BookingDtos.DepositSettlementRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.settleSecurityDeposit(bookingPublicId, req, principal.getId()));
    }
}
