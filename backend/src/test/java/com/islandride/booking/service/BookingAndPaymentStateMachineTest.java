package com.islandride.booking.service;

import com.islandride.booking.domain.Booking;
import com.islandride.booking.domain.BookingStatus;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.InvalidStateTransitionException;
import com.islandride.paymentfinance.domain.Payment;
import com.islandride.paymentfinance.domain.PaymentStatus;
import com.islandride.paymentfinance.domain.Refund;
import com.islandride.paymentfinance.domain.RefundStatus;
import com.islandride.paymentfinance.service.PaymentStateMachine;
import com.islandride.paymentfinance.service.RefundStateMachine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingAndPaymentStateMachineTest {

    private final BookingStateMachine bookingStateMachine = new BookingStateMachine();
    private final PaymentStateMachine paymentStateMachine = new PaymentStateMachine();
    private final RefundStateMachine refundStateMachine = new RefundStateMachine();

    @Test
    void bookingStateMachine_allowsPayAfterApprovalAndEnforcesActorRoles() {
        Instant now = Instant.now();
        Booking booking = new Booking();
        booking.setPublicId("bkg_test1");
        booking.setStatus(BookingStatus.AWAITING_PARTNER_APPROVAL);
        booking.setApprovalExpiresAt(now.plus(2, ChronoUnit.HOURS));

        // Tourist cannot approve their own booking
        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                booking, BookingStatus.APPROVED, BookingActorRole.TOURIST, now))
                .isInstanceOf(InvalidStateTransitionException.class);

        // Partner can approve within deadline
        bookingStateMachine.validateTransition(booking, BookingStatus.APPROVED, BookingActorRole.PARTNER, now);
        booking.setStatus(BookingStatus.APPROVED);
        booking.setPaymentDeadlineAt(now.plus(2, ChronoUnit.HOURS));

        // Partner cannot skip payment to mark APPROVED -> ACTIVE
        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                booking, BookingStatus.ACTIVE, BookingActorRole.PARTNER, now))
                .isInstanceOf(InvalidStateTransitionException.class);

        // System confirms payment within post-approval window
        bookingStateMachine.validateTransition(booking, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM, now.plus(15, ChronoUnit.MINUTES));
        booking.setStatus(BookingStatus.CONFIRMED);

        // Partner marks CONFIRMED -> ACTIVE at pickup, then COMPLETED at return
        bookingStateMachine.validateTransition(booking, BookingStatus.ACTIVE, BookingActorRole.PARTNER, now.plus(3, ChronoUnit.HOURS));
        booking.setStatus(BookingStatus.ACTIVE);

        bookingStateMachine.validateTransition(booking, BookingStatus.COMPLETED, BookingActorRole.PARTNER, now.plus(24, ChronoUnit.HOURS));
        booking.setStatus(BookingStatus.COMPLETED);

        // Terminal COMPLETED state allows no further transitions even by ADMIN
        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                booking, BookingStatus.CANCELLED, BookingActorRole.ADMIN, now.plus(25, ChronoUnit.HOURS)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void bookingStateMachine_rejectsExpiredApprovalAndPaymentWindows() {
        Instant now = Instant.now();
        Booking awaiting = new Booking();
        awaiting.setPublicId("bkg_exp_app");
        awaiting.setStatus(BookingStatus.AWAITING_PARTNER_APPROVAL);
        awaiting.setApprovalExpiresAt(now.minusSeconds(1));

        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                awaiting, BookingStatus.APPROVED, BookingActorRole.PARTNER, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("approval deadline has already expired");

        Booking approved = new Booking();
        approved.setPublicId("bkg_exp_pay");
        approved.setStatus(BookingStatus.APPROVED);
        approved.setPaymentDeadlineAt(now);

        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                approved, BookingStatus.PENDING_PAYMENT, BookingActorRole.TOURIST, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment window deadline has already expired");

        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                approved, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment window deadline has already expired");

        Booking pendingPayment = new Booking();
        pendingPayment.setPublicId("bkg_exp_pending");
        pendingPayment.setStatus(BookingStatus.PENDING_PAYMENT);
        pendingPayment.setPaymentDeadlineAt(now.minusSeconds(5));

        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                pendingPayment, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment window deadline has already expired");

        // System expiry transition remains allowed after payment deadline
        bookingStateMachine.validateTransition(pendingPayment, BookingStatus.EXPIRED, BookingActorRole.SYSTEM, now);

        Booking failedPayment = new Booking();
        failedPayment.setPublicId("bkg_exp_failed");
        failedPayment.setStatus(BookingStatus.PAYMENT_FAILED);
        failedPayment.setPaymentDeadlineAt(now);

        assertThatThrownBy(() -> bookingStateMachine.validateTransition(
                failedPayment, BookingStatus.PENDING_PAYMENT, BookingActorRole.TOURIST, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment window deadline has already expired");

        // Retries and confirmations remain allowed strictly before paymentDeadlineAt
        failedPayment.setPaymentDeadlineAt(now.plusSeconds(60));
        bookingStateMachine.validateTransition(failedPayment, BookingStatus.PENDING_PAYMENT, BookingActorRole.TOURIST, now);
        failedPayment.setStatus(BookingStatus.PENDING_PAYMENT);
        bookingStateMachine.validateTransition(failedPayment, BookingStatus.CONFIRMED, BookingActorRole.SYSTEM, now.plusSeconds(10));
    }

    @Test
    void paymentAndRefundStateMachines_enforceStrictLifecycleTransitions() {
        Instant now = Instant.now();
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("1500.00"));
        payment.setIdempotencyKey("pay-key-1");

        // Cannot refund an uncaptured INITIATED payment
        assertThatThrownBy(() -> paymentStateMachine.transition(payment, PaymentStatus.REFUNDED, "invalid", now))
                .isInstanceOf(InvalidStateTransitionException.class);

        paymentStateMachine.transition(payment, PaymentStatus.CAPTURED, null, now);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getCapturedAt()).isEqualTo(now);

        paymentStateMachine.transition(payment, PaymentStatus.REFUNDED, "cancelled", now.plusSeconds(60));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        assertThatThrownBy(() -> paymentStateMachine.transition(payment, PaymentStatus.FAILED, "too late", now.plusSeconds(120)))
                .isInstanceOf(InvalidStateTransitionException.class);

        Refund refund = new Refund();
        refund.setAmount(new BigDecimal("1500.00"));
        refund.setIdempotencyKey("ref-key-1");

        refundStateMachine.transition(refund, RefundStatus.PROCESSING, null, now);
        refundStateMachine.transition(refund, RefundStatus.FAILED, "Gateway timeout", now.plusSeconds(10));
        assertThat(refund.getRetryCount()).isEqualTo(1);
        assertThat(refund.getErrorMessage()).isEqualTo("Gateway timeout");

        refundStateMachine.transition(refund, RefundStatus.PROCESSING, null, now.plusSeconds(20));
        refundStateMachine.transition(refund, RefundStatus.SUCCESS, null, now.plusSeconds(30));
        assertThat(refund.getErrorMessage()).isNull();

        refundStateMachine.transition(refund, RefundStatus.RECONCILED, null, now.plusSeconds(40));
        assertThat(refund.getReconciledAt()).isEqualTo(now.plusSeconds(40));

        assertThatThrownBy(() -> refundStateMachine.transition(refund, RefundStatus.FAILED, "already done", now.plusSeconds(50)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}

