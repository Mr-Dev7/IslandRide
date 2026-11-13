import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BookingService } from '../../../core/services/booking.service';
import {
  BookingCreationResult,
  InventoryHoldResponse,
  MockPaymentSimulateResponse,
  PaymentMode,
  PricePreviewResponse,
} from '../../../core/models/booking.models';

@Component({
  selector: 'app-tourist-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="checkout-page">
      <div class="checkout-header">
        <a routerLink="/search" class="back-link">← Cancel and Back to Vehicles</a>
        <h1>Secure Vehicle Checkout</h1>
      </div>

      @if (loading()) {
        <div class="card pad">Loading reservation hold details...</div>
      } @else if (error()) {
        <div class="alert alert-error">
          <p><strong>{{ error() }}</strong></p>
          <a routerLink="/search" class="btn btn-primary mt-sm">Return to Vehicle Catalog</a>
        </div>
      } @else if (hold(); as h) {
        <!-- Ticker banner -->
        <div class="hold-ticker" [class.urgent]="secondsRemaining() < 120" [class.expired]="secondsRemaining() <= 0">
          <div class="ticker-content">
            <span class="ticker-icon">⏱</span>
            @if (secondsRemaining() > 0) {
              <span>
                Inventory lock active! Completing checkout reserves this vehicle exclusively for you.
                Time remaining: <strong>{{ formatSeconds(secondsRemaining()) }}</strong>
              </span>
            } @else {
              <span>
                <strong>Your reservation hold has expired.</strong> Please return to the vehicle catalog to re-lock your dates.
              </span>
            }
          </div>
          @if (secondsRemaining() > 0) {
            <button type="button" class="btn-release" (click)="releaseHoldEarly()">Release Hold</button>
          }
        </div>

        <div class="checkout-grid">
          <!-- Left Column: Booking Form -->
          <div class="form-col">
            <div class="card pad">
              <h2>Driver & Rental Verification</h2>
              <p class="muted">
                Government regulations for Andaman & Nicobar require a valid driving license for motorized vehicle rentals.
              </p>

              <div class="field">
                <label for="dlNum">Driving License Number <span class="required">*</span></label>
                <input
                  id="dlNum"
                  type="text"
                  placeholder="e.g. DL-AN01-2023-009988"
                  [(ngModel)]="customerDlNumber"
                  [disabled]="secondsRemaining() <= 0 || submitting()"
                />
              </div>

              <div class="field">
                <label for="emContact">Emergency Contact / WhatsApp Number <span class="required">*</span></label>
                <input
                  id="emContact"
                  type="text"
                  placeholder="e.g. +91 98765 43210"
                  [(ngModel)]="customerEmergencyContact"
                  [disabled]="secondsRemaining() <= 0 || submitting()"
                />
              </div>

              <label class="checkbox-row">
                <input
                  type="checkbox"
                  [(ngModel)]="dlConfirmed"
                  [disabled]="secondsRemaining() <= 0 || submitting()"
                />
                <span>
                  I confirm that I hold a valid, non-expired driving license appropriate for this vehicle class and will present original physical copy at pickup.
                </span>
              </label>

              <h3 class="mt-md">Payment Mode</h3>
              <div class="payment-mode-selector">
                <label class="mode-card" [class.selected]="selectedPaymentMode === 'FULL'">
                  <input
                    type="radio"
                    name="payMode"
                    value="FULL"
                    [(ngModel)]="selectedPaymentMode"
                    (change)="onPaymentModeChanged()"
                    [disabled]="secondsRemaining() <= 0 || submitting()"
                  />
                  <div class="mode-info">
                    <strong>Pay Full Online (100%)</strong>
                    <span>Pay total rental now. Zero balance due at pickup.</span>
                    <strong class="mode-price">₹{{ h.fullQuote.totalPayableAmount | number: '1.2-2' }}</strong>
                  </div>
                </label>

                <label class="mode-card" [class.selected]="selectedPaymentMode === 'PARTIAL_ADVANCE'">
                  <input
                    type="radio"
                    name="payMode"
                    value="PARTIAL_ADVANCE"
                    [(ngModel)]="selectedPaymentMode"
                    (change)="onPaymentModeChanged()"
                    [disabled]="secondsRemaining() <= 0 || submitting()"
                  />
                  <div class="mode-info">
                    <strong>Pay {{ h.partialQuote.advancePercentageApplied }}% Advance Online</strong>
                    <span>Pay ₹{{ h.partialQuote.advanceDueNow | number: '1.2-2' }} now. Balance of ₹{{ h.partialQuote.pickupBalanceDue | number: '1.2-2' }} collected at pickup via UPI or Cash.</span>
                    <strong class="mode-price">₹{{ h.partialQuote.advanceDueNow | number: '1.2-2' }} due now</strong>
                  </div>
                </label>
              </div>

              @if (bookingError()) {
                <div class="alert alert-error mt-sm">{{ bookingError() }}</div>
              }

              <button
                type="button"
                class="btn btn-primary full-width mt-md btn-checkout"
                [disabled]="!canSubmit() || submitting()"
                (click)="proceedToPayment()"
              >
                @if (submitting()) {
                  <span>Processing Reservation...</span>
                } @else if (h.bookingMode === 'APPROVAL') {
                  <span>Request Booking Approval from Partner</span>
                } @else {
                  <span>Proceed to Payment · ₹{{ currentQuote().advanceDueNow | number: '1.2-2' }}</span>
                }
              </button>
            </div>
          </div>

          <!-- Right Column: Summary Card -->
          <div class="summary-col">
            <div class="card pad summary-card">
              <h3>Trip Summary</h3>
              <div class="vehicle-pill">
                <h4>{{ h.vehicleTitle }}</h4>
                <div class="muted small">{{ h.partnerTradeName }} · {{ h.islandName }}</div>
                <div class="badge mt-xs">{{ h.bookingMode === 'APPROVAL' ? 'Host Approval Workflow' : 'Instant Booking' }}</div>
              </div>

              <div class="trip-dates">
                <div>
                  <span class="muted small">Pickup</span>
                  <strong>{{ h.startTime | date: 'medium' }}</strong>
                  <span class="small">{{ h.pickupLocationName || 'Hub Pickup' }}</span>
                </div>
                <div class="divider">→</div>
                <div>
                  <span class="muted small">Return</span>
                  <strong>{{ h.endTime | date: 'medium' }}</strong>
                  <span class="small">{{ h.returnLocationName || 'Hub Return' }}</span>
                </div>
              </div>

              <hr class="summary-hr" />

              <dl class="pricing-table">
                <div>
                  <dt>Base Rental ({{ currentQuote().billableDays }} days)</dt>
                  <dd>₹{{ currentQuote().baseRentalAmount | number: '1.2-2' }}</dd>
                </div>
                @if (currentQuote().seasonalAdjustmentAmount !== 0) {
                  <div>
                    <dt>Seasonal Adjustment</dt>
                    <dd>₹{{ currentQuote().seasonalAdjustmentAmount | number: '1.2-2' }}</dd>
                  </div>
                }
                @if (currentQuote().discountAmount > 0) {
                  <div>
                    <dt>Discount</dt>
                    <dd>-₹{{ currentQuote().discountAmount | number: '1.2-2' }}</dd>
                  </div>
                }
                @if (currentQuote().cleaningFee > 0) {
                  <div>
                    <dt>Cleaning Fee</dt>
                    <dd>₹{{ currentQuote().cleaningFee | number: '1.2-2' }}</dd>
                  </div>
                }
                @if (currentQuote().deliveryFee > 0) {
                  <div>
                    <dt>Delivery Fee</dt>
                    <dd>₹{{ currentQuote().deliveryFee | number: '1.2-2' }}</dd>
                  </div>
                }
                <div>
                  <dt>GST ({{ currentQuote().gstPercentage }}%)</dt>
                  <dd>₹{{ currentQuote().taxAmount | number: '1.2-2' }}</dd>
                </div>
                <div class="row-total">
                  <dt><strong>Total Rental</strong></dt>
                  <dd><strong>₹{{ currentQuote().totalPayableAmount | number: '1.2-2' }}</strong></dd>
                </div>
              </dl>

              <div class="deposit-notice">
                <div class="deposit-header">
                  <span>🛡 Security Deposit</span>
                  <strong>₹{{ currentQuote().securityDepositAmount | number: '1.0-0' }}</strong>
                </div>
                <p class="small muted">
                  Collected {{ currentQuote().depositCollectionMethod === 'AT_PICKUP' ? 'at pickup via Cash or UPI' : 'online at checkout' }}. 100% refundable upon safe return.
                </p>
              </div>

              <div class="cancellation-policy">
                <span class="small">
                  ℹ️ <strong>Cancellation:</strong> Free cancellation up to {{ currentQuote().cancellationDeadlineHours }} hours before pickup.
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Mock Gateway Simulator Modal (Local Dev & Verification) -->
        @if (activeCheckoutOrder(); as order) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>IslandRide Gateway Payment Simulator</h3>
              <p class="muted small">
                Razorpay Test Mock Integration (Zero Real Card/PAN storage). Simulating checkout for order <code>{{ order.gatewayOrderId }}</code>.
              </p>
              <div class="amount-due-box">
                <span class="muted">Amount Payable:</span>
                <h2>₹{{ order.amount | number: '1.2-2' }} {{ order.currency }}</h2>
              </div>

              @if (simulatorStatus()) {
                <div class="alert alert-info mt-sm">{{ simulatorStatus() }}</div>
              }

              <div class="modal-actions">
                <button type="button" class="btn btn-success" (click)="simulatePaymentSuccess(order)" [disabled]="simulating()">
                  {{ simulating() ? 'Processing...' : 'Simulate Successful Payment (Instant)' }}
                </button>
                <button type="button" class="btn btn-warning" (click)="simulateWebhookCapture(order)" [disabled]="simulating()">
                  Simulate Late Webhook Capture
                </button>
                <button type="button" class="btn btn-danger" (click)="simulatePaymentFailure(order)" [disabled]="simulating()">
                  Simulate Payment Failure
                </button>
                <button type="button" class="btn btn-secondary" (click)="activeCheckoutOrder.set(null)">
                  Dismiss Simulator
                </button>
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .checkout-page { max-width: 1050px; margin: 0 auto; padding: 1.5rem 1rem; }
    .checkout-header { margin-bottom: 1.25rem; }
    .checkout-header h1 { font-size: 1.625rem; font-weight: 700; color: var(--ir-navy); margin: 0; }
    .back-link { font-size: 0.875rem; color: var(--ir-teal); font-weight: 600; text-decoration: none; margin-bottom: 0.5rem; display: inline-block; }
    .back-link:hover { text-decoration: underline; }
    .hold-ticker { background: var(--ir-sea-mist); border: 1px solid #c2e0d8; color: var(--ir-navy); padding: 0.875rem 1.25rem; border-radius: var(--ir-radius-md); margin-bottom: 1.25rem; display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .hold-ticker.urgent { background: #fff5f3; border-color: #ffdcd6; color: #c8321d; box-shadow: 0 0 0 1px var(--ir-coral); }
    .hold-ticker.urgent strong { color: var(--ir-coral); }
    .hold-ticker.expired { background: #fee2e2; border-color: #fca5a5; color: #991b1b; }
    .ticker-content { display: flex; align-items: center; gap: 0.625rem; font-size: 0.9375rem; }
    .btn-release { background: #ffffff; border: 1px solid var(--ir-border); color: var(--ir-charcoal); padding: 5px 10px; border-radius: var(--ir-radius-sm); cursor: pointer; font-size: 0.8rem; font-weight: 600; min-height: 36px; transition: all var(--ir-transition); }
    .btn-release:hover { background: #fee2e2; color: #b91c1c; border-color: #fca5a5; }
    .checkout-grid { display: grid; grid-template-columns: 1.6fr 1.1fr; gap: 1.5rem; align-items: start; }
    .card { background: var(--ir-surface); border: 1px solid var(--ir-border); border-radius: var(--ir-radius-md); padding: 1.5rem; box-shadow: var(--ir-shadow-sm); }
    .card h2 { font-size: 1.25rem; font-weight: 700; color: var(--ir-navy); margin: 0 0 0.35rem 0; }
    .field { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.35rem; }
    .field label { font-size: 0.8125rem; font-weight: 600; color: var(--ir-charcoal); }
    .field input, .field select { padding: 0.55rem 0.75rem; border: 1px solid var(--ir-border); border-radius: var(--ir-radius-sm); font-size: 0.875rem; background: #ffffff; color: var(--ir-charcoal); }
    .field input:focus, .field select:focus { outline: none; border-color: var(--ir-teal); box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.15); }
    .required { color: var(--ir-coral); }
    .checkbox-row { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.85rem; color: var(--ir-charcoal); margin: 0.75rem 0; cursor: pointer; }
    .payment-mode-selector { display: flex; flex-direction: column; gap: 0.75rem; margin: 0.75rem 0; }
    .mode-card { display: flex; gap: 0.75rem; align-items: flex-start; padding: 1rem; border: 1px solid var(--ir-border); border-radius: var(--ir-radius-md); cursor: pointer; transition: all var(--ir-transition); background: #ffffff; }
    .mode-card.selected { border-color: var(--ir-teal); background: var(--ir-sea-mist); box-shadow: 0 0 0 1px var(--ir-teal); }
    .mode-info { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.875rem; }
    .mode-info strong { color: var(--ir-navy); font-size: 0.9375rem; }
    .mode-price { color: var(--ir-teal); margin-top: 0.35rem; font-size: 1rem; font-weight: 700; }
    .btn { padding: 0.65rem 1.25rem; border-radius: var(--ir-radius-sm); font-weight: 600; cursor: pointer; border: none; font-size: 0.9375rem; transition: all var(--ir-transition); min-height: 44px; display: inline-flex; align-items: center; justify-content: center; }
    .btn-primary { background: var(--ir-teal); color: #ffffff; }
    .btn-primary:hover:not(:disabled) { background: #0f8275; }
    .btn:disabled { opacity: 0.55; cursor: not-allowed; }
    .full-width { width: 100%; }
    .btn-checkout { padding: 0.85rem; font-size: 1.05rem; }
    .summary-card { background: var(--ir-surface); border: 1px solid var(--ir-border); }
    .summary-card h3 { font-size: 1.125rem; font-weight: 700; color: var(--ir-navy); margin: 0 0 0.75rem 0; }
    .vehicle-pill { background: var(--ir-sea-mist); padding: 0.875rem; border-radius: var(--ir-radius-sm); border: 1px solid #c2e0d8; margin: 0.75rem 0 1rem; }
    .badge { display: inline-block; background: var(--ir-sea-mist); color: var(--ir-navy); font-size: 0.75rem; padding: 2px 8px; border-radius: var(--ir-radius-full); font-weight: 600; border: 1px solid #c2e0d8; }
    .trip-dates { display: flex; justify-content: space-between; font-size: 0.875rem; margin-bottom: 1rem; }
    .trip-dates div { display: flex; flex-direction: column; gap: 0.15rem; }
    .divider { font-size: 1.25rem; color: var(--ir-muted); }
    .summary-hr { border: none; border-top: 1px dashed var(--ir-border); margin: 1rem 0; }
    .pricing-table { display: flex; flex-direction: column; gap: 0.45rem; font-size: 0.875rem; }
    .pricing-table div { display: flex; justify-content: space-between; }
    .row-total { border-top: 1px solid var(--ir-border); padding-top: 0.5rem; margin-top: 0.25rem; font-size: 1.05rem; color: var(--ir-navy); font-weight: 700; }
    .deposit-notice { background: var(--ir-sea-mist); padding: 0.875rem; border-radius: var(--ir-radius-sm); margin: 1rem 0 0.5rem; border: 1px solid #c2e0d8; }
    .deposit-header { display: flex; justify-content: space-between; font-weight: 700; font-size: 0.875rem; color: var(--ir-navy); }
    .cancellation-policy { background: #f0fdf4; border: 1px solid #bbf7d0; color: #15803d; padding: 0.75rem; border-radius: var(--ir-radius-sm); font-size: 0.8125rem; }
    .alert { padding: 0.75rem 1rem; border-radius: var(--ir-radius-sm); font-size: 0.875rem; }
    .alert-error { background: #fef2f2; color: #991b1b; border: 1px solid #fecaca; }
    .alert-info { background: #eff6ff; color: #1e40af; border: 1px solid #bfdbfe; }
    .modal-backdrop { position: fixed; inset: 0; background: rgba(18, 59, 74, 0.65); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem; }
    .modal-card { background: var(--ir-surface); padding: 1.75rem; border-radius: var(--ir-radius-lg); max-width: 500px; width: 100%; box-shadow: var(--ir-shadow-lg); border: 1px solid var(--ir-border); }
    .modal-card h3 { font-size: 1.25rem; font-weight: 700; color: var(--ir-navy); margin: 0 0 0.35rem 0; }
    .amount-due-box { background: var(--ir-sea-mist); border: 1px dashed #c2e0d8; padding: 1rem; border-radius: var(--ir-radius-md); text-align: center; margin: 1rem 0; }
    .amount-due-box h2 { font-size: 1.75rem; font-weight: 800; color: var(--ir-navy); margin: 0.25rem 0 0 0; }
    .modal-actions { display: flex; flex-direction: column; gap: 0.6rem; margin-top: 1.25rem; }
    .btn-success, .btn-warning, .btn-danger { color: #fff; }
    .btn-success { background: #16a34a; }
    .btn-success:hover { background: #15803d; }
    .btn-warning { background: #d97706; }
    .btn-warning:hover { background: #b45309; }
    .btn-danger { background: #dc2626; }
    .btn-danger:hover { background: #b91c1c; }
    .btn-secondary { background: #f1f5f9; color: var(--ir-charcoal); border: 1px solid var(--ir-border); }
    .btn-secondary:hover { background: #e2e8f0; }
    .small { font-size: 0.8125rem; }
    .muted { color: var(--ir-muted); }
    @media (max-width: 850px) { .checkout-grid { grid-template-columns: 1fr; } }
  `]
})
export class TouristCheckoutComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookingService = inject(BookingService);

  holdPublicId = '';
  hold = signal<InventoryHoldResponse | null>(null);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  bookingError = signal<string | null>(null);
  submitting = signal<boolean>(false);
  simulating = signal<boolean>(false);
  simulatorStatus = signal<string | null>(null);

  secondsRemaining = signal<number>(0);
  private timerInterval: any = null;

  customerDlNumber = '';
  customerEmergencyContact = '';
  dlConfirmed = false;
  selectedPaymentMode: PaymentMode = 'FULL';

  activeCheckoutOrder = signal<any | null>(null);
  createdBookingPublicId = signal<string | null>(null);

  ngOnInit(): void {
    this.holdPublicId = this.route.snapshot.paramMap.get('holdPublicId') || '';
    if (!this.holdPublicId) {
      this.error.set('Missing hold identifier.');
      this.loading.set(false);
      return;
    }
    this.fetchHold();
  }

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }

  fetchHold(): void {
    this.bookingService.getHold(this.holdPublicId).subscribe({
      next: (h) => {
        this.hold.set(h);
        this.secondsRemaining.set(h.remainingSeconds);
        this.startTimer();
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.error?.message || 'Inventory hold expired or not found.');
        this.loading.set(false);
      },
    });
  }

  startTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
    this.timerInterval = setInterval(() => {
      const cur = this.secondsRemaining();
      if (cur <= 1) {
        this.secondsRemaining.set(0);
        clearInterval(this.timerInterval);
      } else {
        this.secondsRemaining.set(cur - 1);
      }
    }, 1000);
  }

  formatSeconds(secs: number): string {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  currentQuote(): PricePreviewResponse {
    const h = this.hold();
    if (!h) {
      return {} as PricePreviewResponse;
    }
    return this.selectedPaymentMode === 'PARTIAL_ADVANCE' ? h.partialQuote : h.fullQuote;
  }

  onPaymentModeChanged(): void {
    // triggers reactive recalculation in currentQuote
  }

  canSubmit(): boolean {
    return (
      this.secondsRemaining() > 0 &&
      this.customerDlNumber.trim().length >= 4 &&
      this.customerEmergencyContact.trim().length >= 8 &&
      this.dlConfirmed
    );
  }

  releaseHoldEarly(): void {
    if (confirm('Are you sure you want to release this vehicle reservation hold?')) {
      this.bookingService.releaseHold(this.holdPublicId).subscribe({
        next: () => this.router.navigate(['/search']),
        error: () => this.router.navigate(['/search']),
      });
    }
  }

  proceedToPayment(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.bookingError.set(null);
    const idempKey = `bkg_${this.holdPublicId}_${Date.now()}`;

    this.bookingService
      .createBooking(
        {
          holdPublicId: this.holdPublicId,
          paymentMode: this.selectedPaymentMode,
          deliveryRequested: false,
          customerDlNumber: this.customerDlNumber.trim(),
          customerEmergencyContact: this.customerEmergencyContact.trim(),
          dlConfirmed: this.dlConfirmed,
          idempotencyKey: idempKey,
        },
        idempKey
      )
      .subscribe({
        next: (result: BookingCreationResult) => {
          this.submitting.set(false);
          this.createdBookingPublicId.set(result.booking.summary.publicId);

          if (result.checkoutOrder) {
            this.activeCheckoutOrder.set(result.checkoutOrder);
          } else {
            // APPROVAL workflow or pending partner approval
            this.router.navigate(['/tourist/bookings', result.booking.summary.publicId]);
          }
        },
        error: (err) => {
          this.submitting.set(false);
          this.bookingError.set(err?.error?.detail || err?.error?.message || 'Failed to create booking.');
        },
      });
  }

  simulatePaymentSuccess(order: any): void {
    this.simulating.set(true);
    this.simulatorStatus.set('Generating cryptographic signature...');
    this.bookingService.simulateMockPayment(order.gatewayOrderId, 'CAPTURE_SUCCESS').subscribe({
      next: (simRes: MockPaymentSimulateResponse) => {
        this.simulatorStatus.set('Verifying payment signature with server...');
        const bookingPid = this.createdBookingPublicId() || order.bookingPublicId;
        this.bookingService
          .verifyPayment(
            {
              bookingPublicId: bookingPid,
              gatewayOrderId: simRes.gatewayOrderId,
              gatewayPaymentId: simRes.gatewayPaymentId,
              gatewaySignature: simRes.checkoutSignature,
            },
            `ver_${simRes.gatewayOrderId}`
          )
          .subscribe({
            next: () => {
              this.simulating.set(false);
              this.activeCheckoutOrder.set(null);
              this.router.navigate(['/tourist/bookings', bookingPid]);
            },
            error: (err) => {
              this.simulating.set(false);
              this.simulatorStatus.set('Verification failed: ' + (err?.error?.detail || err?.message));
            },
          });
      },
      error: (err) => {
        this.simulating.set(false);
        this.simulatorStatus.set('Simulation error: ' + (err?.error?.detail || err?.message));
      },
    });
  }

  simulateWebhookCapture(order: any): void {
    this.simulating.set(true);
    this.simulatorStatus.set('Sending signed HMAC webhook event to /api/v1/payments/webhooks/mock...');
    this.bookingService.simulateMockPayment(order.gatewayOrderId, 'WEBHOOK_CAPTURE').subscribe({
      next: (simRes: MockPaymentSimulateResponse) => {
        this.bookingService
          .sendMockWebhook('mock', simRes.webhookPayload, simRes.webhookSignature, simRes.webhookEventId)
          .subscribe({
            next: (whRes) => {
              this.simulating.set(false);
              this.simulatorStatus.set(`Webhook accepted (sigValid=${whRes.signatureValid}). Navigating to booking...`);
              setTimeout(() => {
                const bookingPid = this.createdBookingPublicId() || order.bookingPublicId;
                this.activeCheckoutOrder.set(null);
                this.router.navigate(['/tourist/bookings', bookingPid]);
              }, 1200);
            },
            error: (err) => {
              this.simulating.set(false);
              this.simulatorStatus.set('Webhook error: ' + (err?.error?.detail || err?.message));
            },
          });
      },
      error: (err) => {
        this.simulating.set(false);
        this.simulatorStatus.set('Simulation error: ' + (err?.error?.detail || err?.message));
      },
    });
  }

  simulatePaymentFailure(order: any): void {
    this.simulating.set(true);
    this.simulatorStatus.set('Triggering payment failure simulation...');
    this.bookingService
      .simulateMockPayment(order.gatewayOrderId, 'PAYMENT_FAILED', 'Card expired or insufficient funds')
      .subscribe({
        next: (simRes: MockPaymentSimulateResponse) => {
          this.bookingService
            .sendMockWebhook('mock', simRes.webhookPayload, simRes.webhookSignature, simRes.webhookEventId)
            .subscribe({
              next: () => {
                this.simulating.set(false);
                this.simulatorStatus.set('Payment failed recorded. You may retry payment.');
              },
              error: () => this.simulating.set(false),
            });
        },
        error: () => this.simulating.set(false),
      });
  }
}
