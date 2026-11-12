import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BookingService } from '../../../core/services/booking.service';
import {
  BookingDetailDto,
  CancellationPreviewResponse,
  GatewayCheckoutOrderResponse,
  MockPaymentSimulateResponse,
} from '../../../core/models/booking.models';

@Component({
  selector: 'app-tourist-booking-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="detail-page">
      <div class="detail-nav">
        <a routerLink="/tourist/bookings" class="back-link">← Back to My Reservations</a>
      </div>

      @if (loading()) {
        <div class="card pad">Loading reservation details...</div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (booking(); as b) {
        <div class="header-card card pad">
          <div class="header-left">
            <span class="ref-pill">{{ b.summary.bookingReference }}</span>
            <h1>{{ b.summary.vehicleTitle }}</h1>
            <p class="muted">{{ b.summary.partnerTradeName }} · Andaman Islands</p>
          </div>
          <div class="header-right">
            <span class="badge" [ngClass]="badgeClass(b.summary.status)">{{ b.summary.status }}</span>
            <span class="badge badge-outline">{{ b.summary.paymentSummaryStatus }}</span>
          </div>
        </div>

        <!-- Action Banners -->
        @if (b.summary.status === 'PENDING_PAYMENT' || b.summary.status === 'APPROVED') {
          <div class="action-banner warning">
            <div>
              <strong>Payment Required to Confirm Vehicle</strong>
              <p class="small">
                Please complete payment before <strong>{{ b.summary.paymentDeadlineAt | date: 'medium' }}</strong>. Unpaid reservations are released automatically.
              </p>
            </div>
            <button type="button" class="btn btn-primary" (click)="openPaymentModal(b)">
              Pay Online Now
            </button>
          </div>
        }

        <div class="content-grid">
          <div class="main-col">
            <!-- Rental Itinerary -->
            <div class="card pad">
              <h3>Rental Itinerary</h3>
              <div class="itinerary-grid">
                <div>
                  <span class="muted small">Pickup Hub & Time</span>
                  <strong>{{ b.summary.startTime | date: 'medium' }}</strong>
                  <span>{{ b.summary.pickupLocationName || 'Main Hub' }}</span>
                </div>
                <div class="divider">→</div>
                <div>
                  <span class="muted small">Return Hub & Time</span>
                  <strong>{{ b.summary.endTime | date: 'medium' }}</strong>
                  <span>{{ b.summary.returnLocationName || 'Main Hub' }}</span>
                </div>
              </div>

              <!-- Driver details -->
              <h4 class="mt-md">Driver Verification</h4>
              <div class="driver-info">
                <div><span class="muted small">License Number:</span> <strong>{{ b.customerDlNumber }}</strong></div>
                <div><span class="muted small">Emergency Contact:</span> <strong>{{ b.customerEmergencyContact }}</strong></div>
              </div>

              <!-- Reschedule & Cancellation buttons -->
              @if (b.summary.status === 'CONFIRMED' || b.summary.status === 'APPROVED' || b.summary.status === 'PENDING_PAYMENT') {
                <div class="manage-actions mt-md">
                  <button type="button" class="btn btn-outline" (click)="openRescheduleModal(b)">
                    Reschedule / Change Dates
                  </button>
                  <button type="button" class="btn btn-danger-outline" (click)="openCancelModal(b)">
                    Cancel Reservation
                  </button>
                </div>
              }
            </div>

            <!-- Handover & Inspection records -->
            @if (b.inspections.length > 0) {
              <div class="card pad mt-md">
                <h3>Vehicle Handover Records</h3>
                @for (insp of b.inspections; track insp.publicId) {
                  <div class="inspection-record">
                    <div class="record-head">
                      <strong>{{ insp.handoverType === 'PICKUP' ? 'Vehicle Pickup Handover' : 'Vehicle Return Inspection' }}</strong>
                      <span class="small muted">{{ insp.completedAt | date: 'medium' }}</span>
                    </div>
                    <div class="record-stats">
                      <div><span class="muted small">Odometer:</span> <strong>{{ insp.odometerReading }} km</strong></div>
                      <div><span class="muted small">Fuel Level:</span> <strong>{{ insp.fuelPercentage }}%</strong></div>
                    </div>
                    @if (insp.notes) {
                      <p class="small mt-xs muted">Notes: {{ insp.notes }}</p>
                    }
                  </div>
                }
              </div>
            }

            <!-- Modifications History -->
            @if (b.modifications.length > 0) {
              <div class="card pad mt-md">
                <h3>Reschedule History</h3>
                @for (m of b.modifications; track m.publicId) {
                  <div class="mod-record">
                    <span class="small muted">{{ m.createdAt | date: 'medium' }}</span>
                    <div>
                      Previous: {{ m.previousStartTime | date: 'shortDate' }} → {{ m.previousEndTime | date: 'shortDate' }} |
                      New: {{ m.newStartTime | date: 'shortDate' }} → {{ m.newEndTime | date: 'shortDate' }}
                    </div>
                    <div class="small">Price Delta: ₹{{ m.priceDelta | number: '1.2-2' }} ({{ m.adjustmentType }})</div>
                  </div>
                }
              </div>
            }
          </div>

          <!-- Right Column: Financial Breakdown -->
          <div class="side-col">
            <div class="card pad summary-card">
              <h3>Financial Ledger</h3>
              <dl class="pricing-list">
                <div><dt>Base Rental</dt><dd>₹{{ b.baseRentalAmount | number: '1.2-2' }}</dd></div>
                @if (b.seasonalAdjustmentAmount !== 0) {
                  <div><dt>Seasonal Adjustment</dt><dd>₹{{ b.seasonalAdjustmentAmount | number: '1.2-2' }}</dd></div>
                }
                @if (b.cleaningFee > 0) {
                  <div><dt>Cleaning Fee</dt><dd>₹{{ b.cleaningFee | number: '1.2-2' }}</dd></div>
                }
                @if (b.deliveryFee > 0) {
                  <div><dt>Delivery Fee</dt><dd>₹{{ b.deliveryFee | number: '1.2-2' }}</dd></div>
                }
                <div><dt>GST Taxes</dt><dd>₹{{ b.taxAmount | number: '1.2-2' }}</dd></div>
                <div class="total-row"><dt><strong>Total Payable</strong></dt><dd><strong>₹{{ b.summary.totalPayableAmount | number: '1.2-2' }}</strong></dd></div>
                <div><dt>Online Paid</dt><dd class="text-success">₹{{ b.summary.advancePaidAmount | number: '1.2-2' }}</dd></div>
                <div><dt>Pickup Balance Due</dt><dd [class.text-warning]="b.summary.pickupBalanceDue > 0">₹{{ b.summary.pickupBalanceDue | number: '1.2-2' }}</dd></div>
              </dl>

              <!-- Security Deposit Card -->
              @if (b.securityDeposit; as dep) {
                <div class="deposit-box mt-md">
                  <div class="dep-head">
                    <span>🛡 Security Deposit</span>
                    <strong>₹{{ dep.expectedAmount | number: '1.0-0' }}</strong>
                  </div>
                  <div class="dep-status">
                    <span class="badge" [ngClass]="dep.status === 'COLLECTED' ? 'badge-success' : 'badge-neutral'">{{ dep.status }}</span>
                    <span class="small muted">{{ dep.collectionMethod === 'AT_PICKUP' ? 'Payable at pickup' : 'Online' }}</span>
                  </div>
                  @if (dep.refundedAmount > 0) {
                    <p class="small text-success mt-xs">Refunded: ₹{{ dep.refundedAmount | number: '1.2-2' }}</p>
                  }
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Reschedule Modal -->
        @if (showRescheduleModal()) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Reschedule Reservation</h3>
              <p class="small muted">Change your reservation dates. Self-exclusion guarantees non-overlapping conflicts are ignored.</p>

              <div class="field mt-sm">
                <label>New Pickup Timestamp</label>
                <input type="datetime-local" [(ngModel)]="newStartTime" />
              </div>
              <div class="field mt-sm">
                <label>New Return Timestamp</label>
                <input type="datetime-local" [(ngModel)]="newEndTime" />
              </div>
              <div class="field mt-sm">
                <label>Reason for modification</label>
                <input type="text" [(ngModel)]="modifyReason" placeholder="e.g. Flight timing changed" />
              </div>

              @if (modalError()) {
                <div class="alert alert-error mt-sm">{{ modalError() }}</div>
              }

              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-primary" (click)="submitReschedule(b)" [disabled]="actionLoading()">
                  {{ actionLoading() ? 'Updating...' : 'Confirm Reschedule' }}
                </button>
                <button type="button" class="btn btn-outline" (click)="showRescheduleModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }

        <!-- Cancellation Preview Modal -->
        @if (showCancelModal()) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Cancel Reservation</h3>
              @if (cancellationPreview(); as prev) {
                <div class="cancel-preview-box">
                  <p class="small muted">{{ prev.policyAppliedDescription }}</p>
                  <dl class="pricing-list mt-sm">
                    <div><dt>Rental Paid</dt><dd>₹{{ prev.rentalOnlinePaid | number: '1.2-2' }}</dd></div>
                    <div><dt>Cancellation Penalty</dt><dd class="text-danger">-₹{{ prev.rentalPenaltyAmount | number: '1.2-2' }}</dd></div>
                    <div><dt>Refund to Original Account</dt><dd class="text-success"><strong>₹{{ prev.totalRefundAmount | number: '1.2-2' }}</strong></dd></div>
                  </dl>
                  <p class="small mt-sm" [class.text-success]="prev.depositProtected">
                    {{ prev.depositProtected ? '✓ Security Deposit is 100% protected and waived.' : '' }}
                  </p>
                </div>
              }

              <div class="field mt-sm">
                <label>Cancellation Reason</label>
                <input type="text" [(ngModel)]="cancelReason" placeholder="Reason for cancellation" />
              </div>

              @if (modalError()) {
                <div class="alert alert-error mt-sm">{{ modalError() }}</div>
              }

              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-danger" (click)="submitCancel(b)" [disabled]="actionLoading()">
                  {{ actionLoading() ? 'Cancelling...' : 'Confirm Cancellation' }}
                </button>
                <button type="button" class="btn btn-outline" (click)="showCancelModal.set(false)">Back</button>
              </div>
            </div>
          </div>
        }

        <!-- Payment Simulator Modal -->
        @if (activeOrder(); as order) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Gateway Payment Simulator</h3>
              <p class="muted small">Order: <code>{{ order.gatewayOrderId }}</code></p>
              <div class="amount-box">
                <h2>₹{{ order.amount | number: '1.2-2' }} {{ order.currency }}</h2>
              </div>
              <div class="modal-actions mt-sm">
                <button type="button" class="btn btn-success" (click)="completeMockPayment(b, order)" [disabled]="actionLoading()">
                  Pay Now (Mock Signature Success)
                </button>
                <button type="button" class="btn btn-outline" (click)="activeOrder.set(null)">Close</button>
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .detail-page { max-width: 1050px; margin: 0 auto; }
    .detail-nav { margin-bottom: 1rem; }
    .back-link { font-size: 0.875rem; color: #0284c7; text-decoration: none; }
    .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; }
    .pad { padding: 1.5rem; }
    .header-card { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .ref-pill { display: inline-block; font-size: 0.75rem; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
    .header-right { display: flex; flex-direction: column; gap: 0.4rem; align-items: flex-end; }
    .badge { font-size: 0.8125rem; padding: 3px 10px; border-radius: 999px; font-weight: 600; }
    .badge-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .badge-success { background: #dcfce7; color: #166534; }
    .badge-warning { background: #fef3c7; color: #92400e; }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-danger { background: #fee2e2; color: #991b1b; }
    .badge-neutral { background: #f1f5f9; color: #475569; }
    .action-banner { padding: 1rem 1.5rem; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .action-banner.warning { background: #fef3c7; border: 1px solid #fde68a; color: #92400e; }
    .content-grid { display: grid; grid-template-columns: 1.6fr 1.1fr; gap: 1.5rem; }
    .itinerary-grid { display: flex; justify-content: space-between; align-items: center; background: #f8fafc; padding: 1rem; border-radius: 8px; margin-top: 0.5rem; }
    .itinerary-grid div { display: flex; flex-direction: column; }
    .divider { font-size: 1.5rem; color: #94a3b8; }
    .driver-info { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; background: #f8fafc; padding: 0.75rem 1rem; border-radius: 6px; margin-top: 0.5rem; font-size: 0.875rem; }
    .manage-actions { display: flex; gap: 0.75rem; }
    .btn { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 600; cursor: pointer; border: none; font-size: 0.875rem; }
    .btn-primary { background: #0284c7; color: #fff; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #0284c7; }
    .btn-danger-outline { background: transparent; border: 1px solid #fca5a5; color: #dc2626; }
    .btn-danger { background: #dc2626; color: #fff; }
    .btn-success { background: #16a34a; color: #fff; }
    .summary-card { background: #f8fafc; }
    .pricing-list { display: flex; flex-direction: column; gap: 0.4rem; font-size: 0.875rem; margin-top: 0.5rem; }
    .pricing-list div { display: flex; justify-content: space-between; width: 100%; }
    .total-row { border-top: 1px solid #cbd5e1; padding-top: 0.5rem; margin-top: 0.25rem; font-size: 1rem; }
    .deposit-box { background: #ffffff; border: 1px solid #e2e8f0; padding: 0.85rem; border-radius: 6px; }
    .dep-head { display: flex; justify-content: space-between; font-weight: 600; font-size: 0.875rem; }
    .dep-status { display: flex; justify-content: space-between; align-items: center; margin-top: 0.4rem; }
    .inspection-record, .mod-record { background: #f8fafc; padding: 0.75rem; border-radius: 6px; margin-top: 0.5rem; font-size: 0.875rem; }
    .record-head { display: flex; justify-content: space-between; }
    .record-stats { display: flex; gap: 1.5rem; margin-top: 0.25rem; }
    .text-success { color: #16a34a; }
    .text-warning { color: #d97706; }
    .text-danger { color: #dc2626; }
    .small { font-size: 0.8125rem; }
    .muted { color: #64748b; }
    .mt-xs { margin-top: 0.25rem; }
    .mt-sm { margin-top: 0.5rem; }
    .mt-md { margin-top: 1rem; }
    .modal-backdrop { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .modal-card { background: #ffffff; padding: 1.5rem; border-radius: 8px; max-width: 480px; width: 90%; }
    .modal-actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
    .cancel-preview-box { background: #f8fafc; padding: 0.75rem; border-radius: 6px; margin-top: 0.5rem; }
    .amount-box { text-align: center; margin: 1rem 0; }
    .field { display: flex; flex-direction: column; gap: 0.35rem; }
    .field input { padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; }
    .alert-error { background: #fee2e2; color: #991b1b; padding: 0.5rem; border-radius: 4px; font-size: 0.8125rem; }
    @media (max-width: 850px) { .content-grid { grid-template-columns: 1fr; } }
  `]
})
export class TouristBookingDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookingService = inject(BookingService);

  bookingPublicId = '';
  booking = signal<BookingDetailDto | null>(null);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  showRescheduleModal = signal<boolean>(false);
  newStartTime = '';
  newEndTime = '';
  modifyReason = '';

  showCancelModal = signal<boolean>(false);
  cancelReason = '';
  cancellationPreview = signal<CancellationPreviewResponse | null>(null);

  activeOrder = signal<GatewayCheckoutOrderResponse | null>(null);
  actionLoading = signal<boolean>(false);
  modalError = signal<string | null>(null);

  ngOnInit(): void {
    this.bookingPublicId = this.route.snapshot.paramMap.get('publicId') || '';
    if (!this.bookingPublicId) {
      this.error.set('Missing booking public identifier.');
      this.loading.set(false);
      return;
    }
    this.fetchBooking();
  }

  fetchBooking(): void {
    this.bookingService.getMyBooking(this.bookingPublicId).subscribe({
      next: (b) => {
        this.booking.set(b);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.message || 'Failed to load booking details.');
        this.loading.set(false);
      },
    });
  }

  openPaymentModal(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.bookingService.initiateCheckoutOrder(b.summary.publicId).subscribe({
      next: (order) => {
        this.actionLoading.set(false);
        this.activeOrder.set(order);
      },
      error: (err) => {
        this.actionLoading.set(false);
        alert(err?.error?.detail || 'Could not initiate payment order.');
      },
    });
  }

  completeMockPayment(b: BookingDetailDto, order: GatewayCheckoutOrderResponse): void {
    this.actionLoading.set(true);
    this.bookingService.simulateMockPayment(order.gatewayOrderId, 'CAPTURE_SUCCESS').subscribe({
      next: (simRes: MockPaymentSimulateResponse) => {
        this.bookingService
          .verifyPayment({
            bookingPublicId: b.summary.publicId,
            gatewayOrderId: simRes.gatewayOrderId,
            gatewayPaymentId: simRes.gatewayPaymentId,
            gatewaySignature: simRes.checkoutSignature,
          })
          .subscribe({
            next: () => {
              this.actionLoading.set(false);
              this.activeOrder.set(null);
              this.fetchBooking();
            },
            error: (err) => {
              this.actionLoading.set(false);
              alert(err?.error?.detail || 'Payment verification failed.');
            },
          });
      },
      error: (err) => {
        this.actionLoading.set(false);
        alert(err?.error?.detail || 'Simulation error.');
      },
    });
  }

  openRescheduleModal(b: BookingDetailDto): void {
    this.newStartTime = b.summary.startTime.slice(0, 16);
    this.newEndTime = b.summary.endTime.slice(0, 16);
    this.modifyReason = '';
    this.modalError.set(null);
    this.showRescheduleModal.set(true);
  }

  submitReschedule(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.modalError.set(null);
    const startIso = new Date(this.newStartTime).toISOString();
    const endIso = new Date(this.newEndTime).toISOString();

    this.bookingService
      .modifyBooking(b.summary.publicId, {
        newStartTime: startIso,
        newEndTime: endIso,
        reason: this.modifyReason || 'Tourist requested reschedule',
      })
      .subscribe({
        next: (updated) => {
          this.booking.set(updated);
          this.actionLoading.set(false);
          this.showRescheduleModal.set(false);
        },
        error: (err) => {
          this.actionLoading.set(false);
          this.modalError.set(err?.error?.detail || err?.message || 'Could not reschedule reservation.');
        },
      });
  }

  openCancelModal(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.modalError.set(null);
    this.bookingService.previewCancellation(b.summary.publicId).subscribe({
      next: (prev) => {
        this.cancellationPreview.set(prev);
        this.actionLoading.set(false);
        this.showCancelModal.set(true);
      },
      error: (err) => {
        this.actionLoading.set(false);
        alert(err?.error?.detail || 'Could not calculate cancellation preview.');
      },
    });
  }

  submitCancel(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.modalError.set(null);
    this.bookingService.cancelBooking(b.summary.publicId, this.cancelReason).subscribe({
      next: (cancelled) => {
        this.booking.set(cancelled);
        this.actionLoading.set(false);
        this.showCancelModal.set(false);
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.modalError.set(err?.error?.detail || err?.message || 'Cancellation failed.');
      },
    });
  }

  badgeClass(status: string): string {
    switch (status) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'badge-success';
      case 'ACTIVE':
        return 'badge-info';
      case 'AWAITING_PARTNER_APPROVAL':
      case 'APPROVED':
      case 'PENDING_PAYMENT':
        return 'badge-warning';
      case 'CANCELLED':
      case 'REJECTED':
      case 'EXPIRED':
      case 'NO_SHOW':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }
}
