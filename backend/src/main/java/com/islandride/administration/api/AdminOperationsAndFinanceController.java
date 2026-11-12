package com.islandride.administration.api;

import com.islandride.administration.service.AdminFinanceOverviewService;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.booking.domain.OverbookingExceptionStatus;
import com.islandride.booking.dto.BookingDtos;
import com.islandride.booking.service.BookingOrchestrationService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.paymentfinance.dto.PaymentDtos;
import com.islandride.paymentfinance.service.PaymentOrchestrationService;
import com.islandride.paymentfinance.service.RefundOrchestrationService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN', 'FINANCE_ADMIN', 'SUPPORT_AGENT')")
@Tag(name = "Administrative Operations & Finance", description = "Backoffice oversight for bookings, disruptions, finance ledgers and refunds")
public class AdminOperationsAndFinanceController {

    private final BookingOrchestrationService bookingOrchestrationService;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RefundOrchestrationService refundOrchestrationService;
    private final AdminFinanceOverviewService financeOverviewService;

    public AdminOperationsAndFinanceController(BookingOrchestrationService bookingOrchestrationService,
                                              PaymentOrchestrationService paymentOrchestrationService,
                                              RefundOrchestrationService refundOrchestrationService,
                                              AdminFinanceOverviewService financeOverviewService) {
        this.bookingOrchestrationService = bookingOrchestrationService;
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.refundOrchestrationService = refundOrchestrationService;
        this.financeOverviewService = financeOverviewService;
    }

    @GetMapping("/bookings")
    @Operation(summary = "List paginated system bookings for operations and support")
    public ResponseEntity<Page<BookingDtos.BookingSummaryDto>> listAllBookings(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bookingOrchestrationService.listAdminBookings(pageable));
    }

    @GetMapping("/bookings/{bookingPublicId}")
    @Operation(summary = "Get exhaustive booking detail including payment ledger, modifications and evidence")
    public ResponseEntity<BookingDtos.BookingDetailDto> getAdminBooking(@PathVariable String bookingPublicId) {
        return ResponseEntity.ok(bookingOrchestrationService.getAdminBooking(bookingPublicId));
    }

    @PostMapping("/bookings/{bookingPublicId}/disruption-override")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Admin override for disruption events (cyclone, ferry suspension) with 100% refund/reschedule")
    public ResponseEntity<BookingDtos.BookingDetailDto> disruptionOverride(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingDtos.DisruptionOverrideRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.adminDisruptionOverride(bookingPublicId, req, principal.getId()));
    }

    @PostMapping("/bookings/{bookingPublicId}/dispute/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Arbitrate disputed booking and enforce settlement or refund")
    public ResponseEntity<BookingDtos.BookingDetailDto> resolveDispute(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @Valid @RequestBody BookingDtos.DisputeResolveRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.adminResolveDispute(bookingPublicId, req, principal.getId()));
    }

    @GetMapping("/overbooking-exceptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN', 'FINANCE_ADMIN')")
    @Operation(summary = "List overbooking exceptions triggered by late webhook captures")
    public ResponseEntity<List<BookingDtos.OverbookingExceptionDto>> listOverbookingExceptions(
            @RequestParam(required = false) OverbookingExceptionStatus status) {
        return ResponseEntity.ok(bookingOrchestrationService.listOverbookingExceptions(status));
    }

    @PostMapping("/overbooking-exceptions/{exceptionPublicId}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Acknowledge and mark overbooking exception resolved")
    public ResponseEntity<BookingDtos.OverbookingExceptionDto> resolveOverbooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String exceptionPublicId,
            @Valid @RequestBody BookingDtos.ResolveOverbookingRequest req) {
        return ResponseEntity.ok(bookingOrchestrationService.resolveOverbookingException(exceptionPublicId, req, principal.getId()));
    }

    @GetMapping("/finance/overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    @Operation(summary = "Comprehensive financial ledger overview, collections, commissions, refunds and deposit trust liability")
    public ResponseEntity<PaymentDtos.AdminFinanceOverviewDto> getFinanceOverview() {
        return ResponseEntity.ok(financeOverviewService.getFinanceOverview());
    }

    @PostMapping("/finance/refunds/manual/{bookingPublicId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    @Operation(summary = "Admin manual refund execution on captured booking payment")
    public ResponseEntity<PaymentDtos.RefundSummaryDto> issueManualRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingPublicId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentDtos.ManualRefundRequest req) {
        return ResponseEntity.ok(refundOrchestrationService.createManualRefund(
                bookingPublicId,
                req.amount(),
                req.reason(),
                req.refundType(),
                principal.getId(),
                idempotencyKey
        ));
    }

    @PostMapping("/finance/refunds/{refundPublicId}/retry")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    @Operation(summary = "Retry a failed or pending refund, or mark reconciled")
    public ResponseEntity<PaymentDtos.RefundSummaryDto> retryRefund(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String refundPublicId,
            @RequestParam(defaultValue = "false") boolean forceMarkReconciled) {
        return ResponseEntity.ok(refundOrchestrationService.retryOrReconcileRefund(
                refundPublicId,
                principal.getId(),
                forceMarkReconciled
        ));
    }

    @PostMapping("/finance/reconcile-payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    @Operation(summary = "Trigger active reconciliation sweep for stale initiated gateway payments")
    public ResponseEntity<Map<String, Object>> triggerPaymentReconciliation() {
        int reconciled = paymentOrchestrationService.reconcileStaleInitiatedPayments();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "reconciledPaymentsCount", reconciled));
    }

    @PostMapping("/bookings/expire-sweep")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATIONS_ADMIN')")
    @Operation(summary = "Trigger manual sweep to expire bookings past approval or payment deadlines")
    public ResponseEntity<Map<String, Object>> triggerBookingExpirationSweep() {
        int expired = bookingOrchestrationService.expireStaleBookings();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "expiredBookingsCount", expired));
    }
}
