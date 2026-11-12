import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PartnerBookingService } from '../../core/services/partner-booking.service';
import { BookingDetailDto } from '../../core/models/booking.models';

@Component({
  selector: 'app-partner-booking-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="partner-detail-page">
      <div class="top-nav">
        <a routerLink="/partner/bookings" class="back-link">← Back to Fleet Bookings</a>
      </div>

      @if (loading()) {
        <div class="card pad">Loading reservation details...</div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (booking(); as b) {
        <div class="header-card card pad">
          <div>
            <span class="ref-badge">{{ b.summary.bookingReference }}</span>
            <h2>{{ b.summary.vehicleTitle }}</h2>
            <div class="muted small">Business: {{ b.summary.partnerTradeName }}</div>
          </div>
          <div class="badges-col">
            <span class="badge" [ngClass]="badgeClass(b.summary.status)">{{ b.summary.status }}</span>
            <span class="badge badge-outline">{{ b.summary.paymentSummaryStatus }}</span>
          </div>
        </div>

        <!-- Action Row -->
        <div class="action-bar card pad">
          @if (b.summary.status === 'AWAITING_PARTNER_APPROVAL') {
            <div class="action-group">
              <span>Customer requested reservation:</span>
              <button type="button" class="btn btn-success" (click)="showApproveModal.set(true)">
                ✓ Approve Request
              </button>
              <button type="button" class="btn btn-danger" (click)="showRejectModal.set(true)">
                ✕ Reject Request
              </button>
            </div>
          } @else if (b.summary.status === 'CONFIRMED') {
            <div class="action-group">
              <span>Vehicle confirmed for customer:</span>
              <button type="button" class="btn btn-primary" (click)="openPickupModal(b)">
                🔑 Record Pickup Handover
              </button>
              <button type="button" class="btn btn-warning-outline" (click)="showNoShowModal.set(true)">
                Mark Customer No-Show
              </button>
            </div>
          } @else if (b.summary.status === 'ACTIVE') {
            <div class="action-group">
              <span>Rental active:</span>
              <button type="button" class="btn btn-primary" (click)="openReturnModal(b)">
                🏁 Complete Vehicle Return Inspection
              </button>
            </div>
          } @else {
            <div class="muted small">Booking is in final status {{ b.summary.status }}.</div>
          }
        </div>

        <div class="grid-layout">
          <!-- Left Column -->
          <div class="col-main">
            <!-- Customer and DL verification card -->
            <div class="card pad">
              <h3>Customer & Driving License</h3>
              <div class="details-grid">
                <div><span class="muted small">Name:</span> <strong>{{ b.summary.customerName || 'N/A' }}</strong></div>
                <div><span class="muted small">Email:</span> <strong>{{ b.summary.customerEmail || 'N/A' }}</strong></div>
                <div><span class="muted small">Driving License:</span> <strong>{{ b.customerDlNumber }}</strong></div>
                <div><span class="muted small">Emergency Contact:</span> <strong>{{ b.customerEmergencyContact }}</strong></div>
                <div><span class="muted small">Pickup Hub:</span> <strong>{{ b.summary.pickupLocationName || 'Default Hub' }}</strong></div>
                <div><span class="muted small">Return Hub:</span> <strong>{{ b.summary.returnLocationName || 'Default Hub' }}</strong></div>
                <div><span class="muted small">Start Time:</span> <strong>{{ b.summary.startTime | date: 'medium' }}</strong></div>
                <div><span class="muted small">End Time:</span> <strong>{{ b.summary.endTime | date: 'medium' }}</strong></div>
              </div>
            </div>

            <!-- Inspections List -->
            @if (b.inspections.length > 0) {
              <div class="card pad mt-md">
                <h3>Inspection Records</h3>
                @for (insp of b.inspections; track insp.publicId) {
                  <div class="insp-box">
                    <div class="insp-header">
                      <strong>{{ insp.handoverType === 'PICKUP' ? 'Pickup Inspection' : 'Return Inspection' }}</strong>
                      <span class="small muted">{{ insp.completedAt | date: 'medium' }}</span>
                    </div>
                    <div class="insp-stats">
                      <div>Odometer: <strong>{{ insp.odometerReading }} km</strong></div>
                      <div>Fuel: <strong>{{ insp.fuelPercentage }}%</strong></div>
                    </div>
                    @if (insp.notes) {
                      <p class="small muted mt-xs">Notes: {{ insp.notes }}</p>
                    }
                  </div>
                }
              </div>
            }

            <!-- Offline collections recorded -->
            @if (b.offlineCollections.length > 0) {
              <div class="card pad mt-md">
                <h3>Recorded Physical Collections (Hub Desk)</h3>
                <table class="simple-table">
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>Method</th>
                      <th>Amount</th>
                      <th>Receipt Ref</th>
                      <th>Time</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (c of b.offlineCollections; track c.id) {
                      <tr>
                        <td>{{ c.collectionType }}</td>
                        <td>{{ c.paymentMethod }}</td>
                        <td><strong>₹{{ c.amount | number: '1.2-2' }}</strong></td>
                        <td>{{ c.receiptReference || '-' }}</td>
                        <td class="small muted">{{ c.collectedAt | date: 'short' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>

          <!-- Right Column: Financial Breakdown -->
          <div class="col-side">
            <div class="card pad">
              <h3>Financial Summary</h3>
              <dl class="pricing-list">
                <div><dt>Total Payable</dt><dd>₹{{ b.summary.totalPayableAmount | number: '1.2-2' }}</dd></div>
                <div><dt>Online Paid</dt><dd class="text-success">₹{{ b.summary.advancePaidAmount | number: '1.2-2' }}</dd></div>
                <div><dt>Pickup Balance Due</dt><dd [class.text-warning]="b.summary.pickupBalanceDue > 0"><strong>₹{{ b.summary.pickupBalanceDue | number: '1.2-2' }}</strong></dd></div>
              </dl>

              @if (b.securityDeposit; as dep) {
                <div class="dep-box mt-md">
                  <div class="dep-row">
                    <span>Security Deposit</span>
                    <strong>₹{{ dep.expectedAmount | number: '1.0-0' }}</strong>
                  </div>
                  <div class="small muted mt-xs">Status: <strong>{{ dep.status }}</strong> ({{ dep.inspectionStatus }})</div>
                  @if (dep.status === 'COLLECTED' || dep.status === 'INSPECTION_HOLD') {
                    <div class="mt-sm">
                      <button type="button" class="btn btn-outline btn-sm" (click)="openDepositModal(dep)">
                        Settle Deposit
                      </button>
                    </div>
                  }
                </div>
              }

              @if (b.commission; as comm) {
                <div class="comm-box mt-md">
                  <h4>Platform Commission</h4>
                  <div class="small muted">Rate: {{ comm.commissionRatePercent }}% (Excludes deposit & GST)</div>
                  <div class="comm-amount">Commission: ₹{{ comm.commissionAmount | number: '1.2-2' }}</div>
                  <div class="small text-success">Partner Net Payout: ₹{{ comm.partnerPayoutAmount | number: '1.2-2' }}</div>
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Approval Dialog -->
        @if (showApproveModal()) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Approve Booking Request</h3>
              <p class="small muted">The vehicle will be reserved and the customer given a payment window deadline.</p>
              <div class="field mt-sm">
                <label>Approval Message / Notes (Optional)</label>
                <input type="text" [(ngModel)]="actionReason" placeholder="Vehicle is prepped and inspected" />
              </div>
              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-success" (click)="confirmApprove(b)" [disabled]="actionLoading()">
                  Confirm Approval
                </button>
                <button type="button" class="btn btn-outline" (click)="showApproveModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }

        <!-- Reject Dialog -->
        @if (showRejectModal()) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Reject Booking Request</h3>
              <div class="field mt-sm">
                <label>Rejection Reason <span class="required">*</span></label>
                <input type="text" [(ngModel)]="actionReason" placeholder="e.g. Vehicle requires scheduled maintenance" />
              </div>
              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-danger" (click)="confirmReject(b)" [disabled]="actionLoading() || !actionReason.trim()">
                  Confirm Rejection
                </button>
                <button type="button" class="btn btn-outline" (click)="showRejectModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }

        <!-- Pickup Handover Modal -->
        @if (showPickupModal()) {
          <div class="modal-backdrop">
            <div class="modal-card modal-lg">
              <h3>Record Physical Pickup Handover</h3>
              <p class="small muted">Confirm vehicle inspection and collect balance & security deposit.</p>

              <div class="form-row mt-sm">
                <div class="field">
                  <label>Odometer Reading (KM) <span class="required">*</span></label>
                  <input type="number" [(ngModel)]="pickupOdometer" min="0" />
                </div>
                <div class="field">
                  <label>Fuel Percentage (%) <span class="required">*</span></label>
                  <input type="number" [(ngModel)]="pickupFuel" min="0" max="100" />
                </div>
              </div>

              <!-- Balance collection -->
              <h4 class="mt-sm">Pickup Balance Collection</h4>
              <div class="form-row">
                <div class="field">
                  <label>Balance Amount (₹)</label>
                  <input type="number" [(ngModel)]="balanceAmount" />
                </div>
                <div class="field">
                  <label>Payment Method</label>
                  <select [(ngModel)]="balanceMethod">
                    <option value="UPI_QR">UPI QR Code</option>
                    <option value="CASH">Cash</option>
                  </select>
                </div>
                <div class="field">
                  <label>Receipt Reference</label>
                  <input type="text" [(ngModel)]="balanceReceipt" placeholder="e.g. UPI-TXN-12345" />
                </div>
              </div>

              <!-- Security Deposit collection -->
              <h4 class="mt-sm">Security Deposit</h4>
              <label class="checkbox-row">
                <input type="checkbox" [(ngModel)]="depositWaived" />
                <span>Waive security deposit for this rental</span>
              </label>

              @if (!depositWaived) {
                <div class="form-row">
                  <div class="field">
                    <label>Deposit Amount (₹)</label>
                    <input type="number" [(ngModel)]="depositAmount" />
                  </div>
                  <div class="field">
                    <label>Deposit Channel</label>
                    <select [(ngModel)]="depositMethod">
                      <option value="CASH">Cash Deposit</option>
                      <option value="UPI_QR">UPI Hold</option>
                    </select>
                  </div>
                  <div class="field">
                    <label>Receipt Reference</label>
                    <input type="text" [(ngModel)]="depositReceipt" placeholder="e.g. CASH-RCP-001" />
                  </div>
                </div>
              }

              <div class="field mt-sm">
                <label>Handover Inspection Notes</label>
                <input type="text" [(ngModel)]="handoverNotes" placeholder="Clean exterior, full tank, helmet provided" />
              </div>

              @if (actionError()) {
                <div class="alert alert-error mt-sm">{{ actionError() }}</div>
              }

              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-primary" (click)="confirmPickupHandover(b)" [disabled]="actionLoading()">
                  {{ actionLoading() ? 'Saving...' : 'Confirm Handover & Activate Rental' }}
                </button>
                <button type="button" class="btn btn-outline" (click)="showPickupModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }

        <!-- Return Inspection Modal -->
        @if (showReturnModal()) {
          <div class="modal-backdrop">
            <div class="modal-card modal-lg">
              <h3>Complete Vehicle Return Inspection</h3>
              <p class="small muted">Record final mileage, fuel, and settle security deposit disposition.</p>

              <div class="form-row mt-sm">
                <div class="field">
                  <label>Return Odometer (KM) <span class="required">*</span></label>
                  <input type="number" [(ngModel)]="returnOdometer" />
                </div>
                <div class="field">
                  <label>Return Fuel Percentage (%) <span class="required">*</span></label>
                  <input type="number" [(ngModel)]="returnFuel" min="0" max="100" />
                </div>
              </div>

              <label class="checkbox-row mt-sm">
                <input type="checkbox" [(ngModel)]="damageReported" />
                <span class="text-danger">Report vehicle damage or incident</span>
              </label>

              @if (damageReported) {
                <div class="field mt-sm">
                  <label>Estimated Damage Claim Amount (₹)</label>
                  <input type="number" [(ngModel)]="damageClaimAmount" />
                </div>
              }

              @if (!damageReported) {
                <label class="checkbox-row mt-sm">
                  <input type="checkbox" [(ngModel)]="releaseDepositImmediately" />
                  <span>Release and refund security deposit immediately in full</span>
                </label>
              }

              <div class="field mt-sm">
                <label>Return Inspection Notes</label>
                <input type="text" [(ngModel)]="returnNotes" placeholder="Returned in good order, helmet returned" />
              </div>

              @if (actionError()) {
                <div class="alert alert-error mt-sm">{{ actionError() }}</div>
              }

              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-success" (click)="confirmReturnComplete(b)" [disabled]="actionLoading()">
                  {{ actionLoading() ? 'Completing...' : 'Complete Return & Settle Rental' }}
                </button>
                <button type="button" class="btn btn-outline" (click)="showReturnModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }

        <!-- No-Show Dialog -->
        @if (showNoShowModal()) {
          <div class="modal-backdrop">
            <div class="modal-card">
              <h3>Claim Customer No-Show</h3>
              <p class="small muted">Mark booking as NO_SHOW if customer failed to appear after pickup grace window.</p>
              <div class="field mt-sm">
                <label>Evidence Notes / Details</label>
                <input type="text" [(ngModel)]="noShowNotes" placeholder="Attempted phone contact 3 times, no response" />
              </div>
              <div class="modal-actions mt-md">
                <button type="button" class="btn btn-danger" (click)="confirmNoShow(b)" [disabled]="actionLoading()">
                  Confirm No-Show
                </button>
                <button type="button" class="btn btn-outline" (click)="showNoShowModal.set(false)">Cancel</button>
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .partner-detail-page { max-width: 1050px; margin: 0 auto; }
    .top-nav { margin-bottom: 1rem; }
    .back-link { font-size: 0.875rem; color: #0284c7; text-decoration: none; }
    .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; }
    .pad { padding: 1.25rem 1.5rem; }
    .header-card { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .ref-badge { font-size: 0.75rem; background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
    .badges-col { display: flex; flex-direction: column; gap: 0.35rem; align-items: flex-end; }
    .action-bar { margin-bottom: 1rem; background: #f8fafc; }
    .action-group { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; }
    .grid-layout { display: grid; grid-template-columns: 1.6fr 1.1fr; gap: 1.25rem; }
    .details-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-top: 0.75rem; font-size: 0.875rem; }
    .pricing-list { display: flex; flex-direction: column; gap: 0.4rem; font-size: 0.875rem; margin-top: 0.5rem; }
    .pricing-list div { display: flex; justify-content: space-between; width: 100%; }
    .dep-box, .comm-box { background: #f8fafc; padding: 0.75rem; border-radius: 6px; font-size: 0.875rem; }
    .dep-row { display: flex; justify-content: space-between; font-weight: 600; }
    .comm-amount { font-weight: 600; color: #0369a1; margin: 0.25rem 0; }
    .insp-box { background: #f8fafc; padding: 0.75rem; border-radius: 6px; margin-top: 0.5rem; font-size: 0.875rem; }
    .insp-header { display: flex; justify-content: space-between; }
    .insp-stats { display: flex; gap: 1.5rem; margin-top: 0.25rem; }
    .simple-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; margin-top: 0.5rem; }
    .simple-table th, .simple-table td { padding: 0.5rem; border-bottom: 1px solid #e2e8f0; text-align: left; }
    .badge { font-size: 0.8125rem; padding: 3px 10px; border-radius: 999px; font-weight: 600; }
    .badge-outline { background: transparent; border: 1px solid #cbd5e1; color: #475569; }
    .badge-success { background: #dcfce7; color: #166534; }
    .badge-warning { background: #fef3c7; color: #92400e; }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-danger { background: #fee2e2; color: #991b1b; }
    .badge-neutral { background: #f1f5f9; color: #475569; }
    .btn { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 600; cursor: pointer; border: none; font-size: 0.875rem; }
    .btn-primary { background: #0284c7; color: #fff; }
    .btn-success { background: #16a34a; color: #fff; }
    .btn-danger { background: #dc2626; color: #fff; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #0284c7; }
    .btn-warning-outline { background: transparent; border: 1px solid #fde68a; color: #b45309; }
    .btn-sm { padding: 0.35rem 0.65rem; font-size: 0.75rem; }
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
    .modal-lg { max-width: 600px; }
    .modal-actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
    .field { display: flex; flex-direction: column; gap: 0.35rem; }
    .field input, .field select { padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 0.875rem; }
    .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
    .checkbox-row { display: flex; gap: 0.5rem; align-items: center; font-size: 0.875rem; cursor: pointer; }
    .required { color: #dc2626; }
    .alert-error { background: #fee2e2; color: #991b1b; padding: 0.5rem; border-radius: 4px; font-size: 0.8125rem; }
    @media (max-width: 850px) { .grid-layout { grid-template-columns: 1fr; } }
  `]
})
export class PartnerBookingDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly partnerBookingService = inject(PartnerBookingService);

  bookingPublicId = '';
  booking = signal<BookingDetailDto | null>(null);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  actionLoading = signal<boolean>(false);
  actionError = signal<string | null>(null);

  showApproveModal = signal<boolean>(false);
  showRejectModal = signal<boolean>(false);
  actionReason = '';

  showPickupModal = signal<boolean>(false);
  pickupOdometer = 0;
  pickupFuel = 100;
  balanceAmount = 0;
  balanceMethod = 'UPI_QR';
  balanceReceipt = '';
  depositWaived = false;
  depositAmount = 0;
  depositMethod = 'CASH';
  depositReceipt = '';
  handoverNotes = '';

  showReturnModal = signal<boolean>(false);
  returnOdometer = 0;
  returnFuel = 100;
  damageReported = false;
  damageClaimAmount = 0;
  releaseDepositImmediately = true;
  returnNotes = '';

  showNoShowModal = signal<boolean>(false);
  noShowNotes = '';

  ngOnInit(): void {
    this.bookingPublicId = this.route.snapshot.paramMap.get('publicId') || '';
    if (!this.bookingPublicId) {
      this.error.set('Missing booking identifier.');
      this.loading.set(false);
      return;
    }
    this.fetchBooking();
  }

  fetchBooking(): void {
    this.partnerBookingService.getBooking(this.bookingPublicId).subscribe({
      next: (b) => {
        this.booking.set(b);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.message || 'Failed to load fleet booking.');
        this.loading.set(false);
      },
    });
  }

  confirmApprove(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.partnerBookingService.approveBooking(b.summary.publicId, this.actionReason).subscribe({
      next: (res) => {
        this.booking.set(res);
        this.actionLoading.set(false);
        this.showApproveModal.set(false);
      },
      error: (err) => {
        this.actionLoading.set(false);
        alert(err?.error?.detail || 'Approval failed.');
      },
    });
  }

  confirmReject(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.partnerBookingService.rejectBooking(b.summary.publicId, this.actionReason).subscribe({
      next: (res) => {
        this.booking.set(res);
        this.actionLoading.set(false);
        this.showRejectModal.set(false);
      },
      error: (err) => {
        this.actionLoading.set(false);
        alert(err?.error?.detail || 'Rejection failed.');
      },
    });
  }

  openPickupModal(b: BookingDetailDto): void {
    this.pickupOdometer = 12000;
    this.pickupFuel = 100;
    this.balanceAmount = b.summary.pickupBalanceDue;
    this.balanceMethod = 'UPI_QR';
    this.balanceReceipt = '';
    this.depositWaived = false;
    this.depositAmount = b.securityDeposit ? b.securityDeposit.expectedAmount : 0;
    this.depositMethod = 'CASH';
    this.depositReceipt = '';
    this.handoverNotes = '';
    this.actionError.set(null);
    this.showPickupModal.set(true);
  }

  confirmPickupHandover(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.partnerBookingService
      .recordPickupHandover(b.summary.publicId, {
        odometerReading: this.pickupOdometer,
        fuelPercentage: this.pickupFuel,
        checklistDataJson: '{}',
        notes: this.handoverNotes,
        balanceCollectedAmount: this.balanceAmount > 0 ? this.balanceAmount : null,
        balancePaymentMethod: this.balanceMethod,
        balanceReceiptReference: this.balanceReceipt || null,
        depositCollectedAmount: !this.depositWaived && this.depositAmount > 0 ? this.depositAmount : null,
        depositPaymentMethod: this.depositMethod,
        depositReceiptReference: this.depositReceipt || null,
        depositWaived: this.depositWaived,
      })
      .subscribe({
        next: (res) => {
          this.booking.set(res);
          this.actionLoading.set(false);
          this.showPickupModal.set(false);
        },
        error: (err) => {
          this.actionLoading.set(false);
          this.actionError.set(err?.error?.detail || err?.message || 'Handover failed.');
        },
      });
  }

  openReturnModal(b: BookingDetailDto): void {
    const pickupInsp = b.inspections.find((i) => i.handoverType === 'PICKUP');
    this.returnOdometer = pickupInsp ? pickupInsp.odometerReading + 50 : 12050;
    this.returnFuel = 100;
    this.damageReported = false;
    this.damageClaimAmount = 0;
    this.releaseDepositImmediately = true;
    this.returnNotes = '';
    this.actionError.set(null);
    this.showReturnModal.set(true);
  }

  confirmReturnComplete(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.actionError.set(null);
    this.partnerBookingService
      .recordReturnCompletion(b.summary.publicId, {
        odometerReading: this.returnOdometer,
        fuelPercentage: this.returnFuel,
        checklistDataJson: '{}',
        notes: this.returnNotes,
        damageReported: this.damageReported,
        damageClaimAmount: this.damageReported ? this.damageClaimAmount : null,
        releaseDepositImmediately: this.releaseDepositImmediately,
      })
      .subscribe({
        next: (res) => {
          this.booking.set(res);
          this.actionLoading.set(false);
          this.showReturnModal.set(false);
        },
        error: (err) => {
          this.actionLoading.set(false);
          this.actionError.set(err?.error?.detail || err?.message || 'Return completion failed.');
        },
      });
  }

  confirmNoShow(b: BookingDetailDto): void {
    this.actionLoading.set(true);
    this.partnerBookingService
      .markNoShow(b.summary.publicId, { evidenceNotes: this.noShowNotes })
      .subscribe({
        next: (res) => {
          this.booking.set(res);
          this.actionLoading.set(false);
          this.showNoShowModal.set(false);
        },
        error: (err) => {
          this.actionLoading.set(false);
          alert(err?.error?.detail || 'Could not mark no-show.');
        },
      });
  }

  openDepositModal(dep: any): void {
    const release = prompt(`Enter release amount to tourist (Current held: ₹${dep.collectedAmount}):`, String(dep.collectedAmount));
    if (release !== null) {
      this.partnerBookingService
        .settleDeposit(this.bookingPublicId, {
          releaseAmount: Number(release),
          forfeitAmount: Math.max(0, dep.collectedAmount - Number(release)),
          notes: 'Settled by partner staff',
        })
        .subscribe({
          next: (b) => this.booking.set(b),
          error: (err) => alert(err?.error?.detail || 'Settlement failed.'),
        });
    }
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
