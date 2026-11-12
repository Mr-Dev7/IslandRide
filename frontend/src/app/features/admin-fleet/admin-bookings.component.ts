import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminBookingFinanceService } from '../../core/services/admin-booking-finance.service';
import {
  BookingDetailDto,
  BookingSummaryDto,
  OverbookingExceptionDto,
} from '../../core/models/booking.models';
import type { BookingStatus, DisruptionExceptionType } from '../../core/models/booking.models';

@Component({
  selector: 'app-admin-bookings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Operations & Bookings Governance</h1>
          <p class="muted">Manage island-wide bookings, resolve disputes, apply disruption overrides, and manage overbooking exceptions.</p>
        </div>
        <div class="flex gap-2">
          <button (click)="triggerExpirationSweep()" [disabled]="sweepLoading()" class="ir-btn ir-btn-outline">
            {{ sweepLoading() ? 'Sweeping...' : 'Run Expiration Sweep' }}
          </button>
          <a routerLink="/admin/finance" class="ir-btn ir-btn-primary">Finance & Trust Overview</a>
        </div>
      </div>

      @if (alert()) {
        <div class="ir-alert" [ngClass]="alert()!.type === 'error' ? 'ir-alert-error' : 'ir-alert-success'">
          {{ alert()!.message }}
        </div>
      }

      <!-- Tab navigation -->
      <div class="tabs-bar">
        <button [class.active]="activeTab() === 'bookings'" (click)="activeTab.set('bookings')">
          All Bookings ({{ totalBookings() }})
        </button>
        <button [class.active]="activeTab() === 'exceptions'" (click)="activeTab.set('exceptions')">
          Overbooking Exceptions ({{ exceptions().length }})
        </button>
      </div>

      <!-- Tab: Bookings -->
      @if (activeTab() === 'bookings') {
        <div class="section-card">
          <div class="filter-bar">
            <div class="filter-group">
              <label>Filter Status:</label>
              <select [(ngModel)]="statusFilter" (change)="filterBookings()" class="ir-input">
                <option value="ALL">All Statuses</option>
                <option value="AWAITING_PARTNER_APPROVAL">Awaiting Approval</option>
                <option value="APPROVED">Approved (Unpaid)</option>
                <option value="CONFIRMED">Confirmed</option>
                <option value="ACTIVE">Active (On Trip)</option>
                <option value="DISPUTED">Disputed</option>
                <option value="OVERBOOKING_EXCEPTION">Overbooking Exception</option>
                <option value="CANCELLED">Cancelled</option>
                <option value="COMPLETED">Completed</option>
              </select>
            </div>
            <button (click)="loadBookings()" class="ir-btn ir-btn-outline">Refresh</button>
          </div>

          @if (loading()) {
            <p class="muted py-8 text-center">Loading bookings...</p>
          } @else if (filteredBookings().length === 0) {
            <p class="muted py-8 text-center">No bookings match the selected criteria.</p>
          } @else {
            <div class="ir-table-wrapper">
              <table class="ir-table">
                <thead>
                  <tr>
                    <th>Booking Code</th>
                    <th>Vehicle</th>
                    <th>Schedule</th>
                    <th>Mode / Amount</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (b of filteredBookings(); track b.publicId) {
                    <tr>
                      <td>
                        <strong>{{ b.bookingReference }}</strong>
                        <div class="text-xs muted">{{ b.publicId }}</div>
                      </td>
                      <td>
                        <div>{{ b.vehicleTitle }}</div>
                        <div class="text-xs muted">{{ b.vehiclePublicId }}</div>
                      </td>
                      <td>
                        <div class="text-xs">{{ b.startTime | date:'mediumDate' }} {{ b.startTime | date:'shortTime' }}</div>
                        <div class="text-xs muted">to {{ b.endTime | date:'mediumDate' }} {{ b.endTime | date:'shortTime' }}</div>
                      </td>
                      <td>
                        <div>₹{{ b.totalPayableAmount }}</div>
                        <span class="badge" [ngClass]="b.paymentMode === 'FULL' ? 'badge-info' : 'badge-warn'">
                          {{ b.paymentMode }}
                        </span>
                      </td>
                      <td>
                        <span class="badge" [ngClass]="getStatusBadge(b.status)">{{ b.status }}</span>
                        @if (b.overbookingExceptionStatus !== 'NONE') {
                          <div class="text-xs text-rose-600 mt-1">{{ b.overbookingExceptionStatus }}</div>
                        }
                      </td>
                      <td>
                        <div class="action-buttons">
                          <button (click)="openDetail(b.publicId)" class="ir-btn ir-btn-sm ir-btn-outline">Details</button>
                          @if (b.status === 'CONFIRMED' || b.status === 'ACTIVE' || b.status === 'APPROVED') {
                            <button (click)="openDisruptionModal(b)" class="ir-btn ir-btn-sm ir-btn-warn">Disruption</button>
                          }
                          @if (b.status === 'DISPUTED') {
                            <button (click)="openDisputeModal(b)" class="ir-btn ir-btn-sm ir-btn-danger">Resolve Dispute</button>
                          }
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <!-- Tab: Overbooking Exceptions -->
      @if (activeTab() === 'exceptions') {
        <div class="section-card">
          <h2>Overbooking Exceptions Queue</h2>
          <p class="muted text-sm mb-4">Exceptions raised when late captures or inventory conflicts require administrative intervention.</p>
          @if (exceptions().length === 0) {
            <p class="muted py-8 text-center">No open overbooking exceptions. All queues healthy!</p>
          } @else {
            <div class="ir-table-wrapper">
              <table class="ir-table">
                <thead>
                  <tr>
                    <th>Created At</th>
                    <th>Booking / Vehicle</th>
                    <th>Attempted Dates</th>
                    <th>Reason</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ex of exceptions(); track ex.publicId) {
                    <tr>
                      <td class="text-xs">{{ ex.detectedAt | date:'short' }}</td>
                      <td>
                        <div>Ref: {{ ex.bookingReference }}</div>
                        <div class="text-xs muted">{{ ex.bookingPublicId }}</div>
                      </td>
                      <td class="text-xs">
                        Conflicting: {{ ex.conflictingBookingReference || 'None' }}
                      </td>
                      <td class="text-xs">{{ ex.publicId }}</td>
                      <td>
                        <span class="badge" [ngClass]="ex.status === 'OPEN' ? 'badge-danger' : 'badge-success'">
                          {{ ex.status }}
                        </span>
                      </td>
                      <td>
                        @if (ex.status === 'OPEN') {
                          <button (click)="openResolveExceptionModal(ex)" class="ir-btn ir-btn-sm ir-btn-primary">Resolve</button>
                        } @else {
                          <span class="text-xs muted">{{ ex.resolutionNotes || 'Resolved' }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }

      <!-- Modal: Disruption Override -->
      @if (showDisruptionModal()) {
        <div class="ir-modal-backdrop">
          <div class="ir-modal">
            <h3>Admin Disruption Override</h3>
            <p class="muted text-sm">Force disruption handling (weather, cyclone, ferry cancellation) with full refund protection.</p>
            <div class="ir-form-group mt-4">
              <label>Disruption Reason Type</label>
              <select [(ngModel)]="disruptionForm.type" class="ir-input">
                <option value="WEATHER_DISRUPTION">Weather Disruption (Cyclone / Heavy Sea)</option>
                <option value="FERRY_CANCELLATION">Ferry Cancellation (Makruzz / Green Ocean / DSS)</option>
                <option value="ADMIN_FORCE_MAJEURE">Admin Force Majeure</option>
              </select>
            </div>
            <div class="ir-form-group">
              <label>Details / Official Notice</label>
              <textarea [(ngModel)]="disruptionForm.reason" rows="3" class="ir-input" placeholder="e.g. Directorate of Shipping Services cancelled all Swaraj Dweep ferries due to storm alert."></textarea>
            </div>
            <div class="ir-form-group checkbox-row">
              <input type="checkbox" [(ngModel)]="disruptionForm.preferReschedule" id="chkResched" />
              <label for="chkResched">Offer tourist priority reschedule voucher instead of immediate refund</label>
            </div>
            <div class="modal-actions">
              <button (click)="showDisruptionModal.set(false)" class="ir-btn ir-btn-outline">Cancel</button>
              <button (click)="executeDisruptionOverride()" [disabled]="actionLoading() || !disruptionForm.reason" class="ir-btn ir-btn-danger">
                {{ actionLoading() ? 'Applying...' : 'Apply Disruption Override' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal: Dispute Resolution -->
      @if (showDisputeModal()) {
        <div class="ir-modal-backdrop">
          <div class="ir-modal">
            <h3>Resolve Booking Dispute</h3>
            <p class="muted text-sm">Review evidence and record an authoritative binding resolution.</p>
            <div class="ir-form-group mt-4">
              <label>Target Status</label>
              <select [(ngModel)]="disputeForm.targetStatus" class="ir-input">
                <option value="COMPLETED">COMPLETED (Settle in favor of Partner)</option>
                <option value="CANCELLED">CANCELLED (Settle in favor of Tourist)</option>
              </select>
            </div>
            <div class="ir-form-group">
              <label>Refund Amount (₹)</label>
              <input type="number" [(ngModel)]="disputeForm.refundAmount" class="ir-input" placeholder="0.00" />
            </div>
            <div class="ir-form-group">
              <label>Deposit Forfeit Amount (₹)</label>
              <input type="number" [(ngModel)]="disputeForm.depositForfeitAmount" class="ir-input" placeholder="0.00" />
            </div>
            <div class="ir-form-group">
              <label>Resolution Notes</label>
              <textarea [(ngModel)]="disputeForm.resolutionNotes" rows="3" class="ir-input" placeholder="State reason and agreement details."></textarea>
            </div>
            <div class="modal-actions">
              <button (click)="showDisputeModal.set(false)" class="ir-btn ir-btn-outline">Cancel</button>
              <button (click)="executeDisputeResolution()" [disabled]="actionLoading() || !disputeForm.resolutionNotes" class="ir-btn ir-btn-primary">
                {{ actionLoading() ? 'Resolving...' : 'Submit Resolution' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal: Resolve Overbooking Exception -->
      @if (showExceptionModal()) {
        <div class="ir-modal-backdrop">
          <div class="ir-modal">
            <h3>Resolve Overbooking Exception</h3>
            <p class="muted text-sm">Record administrative actions taken (alternative vehicle arranged or refund verified).</p>
            <div class="ir-form-group mt-4">
              <label>Resolution Notes</label>
              <textarea [(ngModel)]="exceptionResolutionNotes" rows="3" class="ir-input" placeholder="e.g. Reassigned tourist to equivalent Category B vehicle or verified refund credit."></textarea>
            </div>
            <div class="modal-actions">
              <button (click)="showExceptionModal.set(false)" class="ir-btn ir-btn-outline">Cancel</button>
              <button (click)="executeResolveException()" [disabled]="actionLoading() || !exceptionResolutionNotes" class="ir-btn ir-btn-primary">
                {{ actionLoading() ? 'Saving...' : 'Resolve Exception' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Modal: Booking Detail Slide-over / Dialog -->
      @if (selectedBooking()) {
        <div class="ir-modal-backdrop">
          <div class="ir-modal ir-modal-lg">
            <div class="flex justify-between items-center mb-4">
              <h3>Booking Detail: {{ selectedBooking()!.summary.bookingReference }}</h3>
              <button (click)="selectedBooking.set(null)" class="ir-btn ir-btn-sm ir-btn-outline">Close</button>
            </div>
            <div class="grid grid-cols-2 gap-4 text-sm mb-4">
              <div><strong>Status:</strong> <span class="badge" [ngClass]="getStatusBadge(selectedBooking()!.summary.status)">{{ selectedBooking()!.summary.status }}</span></div>
              <div><strong>Vehicle:</strong> {{ selectedBooking()!.summary.vehicleTitle }} ({{ selectedBooking()!.summary.vehiclePublicId }})</div>
              <div><strong>Pickup:</strong> {{ selectedBooking()!.summary.startTime | date:'medium' }}</div>
              <div><strong>Return:</strong> {{ selectedBooking()!.summary.endTime | date:'medium' }}</div>
              <div><strong>Total Payable:</strong> ₹{{ selectedBooking()!.summary.totalPayableAmount }}</div>
              <div><strong>Partner:</strong> {{ selectedBooking()!.summary.partnerTradeName }}</div>
              <div><strong>Payment Status:</strong> {{ selectedBooking()!.summary.paymentSummaryStatus }}</div>
              <div><strong>Emergency Contact:</strong> {{ selectedBooking()!.customerEmergencyContact }}</div>
            </div>

            @if (selectedBooking()!.payments && selectedBooking()!.payments.length > 0) {
              <h4 class="font-bold mt-4 mb-2">Payments</h4>
              <table class="ir-table text-xs">
                <thead><tr><th>Type</th><th>Method</th><th>Amount</th><th>Status</th><th>Gateway Order</th></tr></thead>
                <tbody>
                  @for (p of selectedBooking()!.payments; track p.publicId) {
                    <tr>
                      <td>{{ p.paymentType }}</td>
                      <td>{{ p.paymentMethod }}</td>
                      <td>₹{{ p.amount }}</td>
                      <td>{{ p.status }}</td>
                      <td>{{ p.gatewayOrderId || '-' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }

            @if (selectedBooking()!.refunds && selectedBooking()!.refunds.length > 0) {
              <h4 class="font-bold mt-4 mb-2">Refunds</h4>
              <table class="ir-table text-xs">
                <thead><tr><th>Type</th><th>Amount</th><th>Status</th><th>Failure Reason</th></tr></thead>
                <tbody>
                  @for (r of selectedBooking()!.refunds; track r.publicId) {
                    <tr>
                      <td>{{ r.refundType }}</td>
                      <td>₹{{ r.amount }}</td>
                      <td>{{ r.status }}</td>
                      <td>{{ r.errorMessage || '-' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .ir-page { padding: 1.5rem; max-width: 1200px; margin: 0 auto; }
    .page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .muted { color: #64748b; }
    .tabs-bar { display: flex; gap: 1rem; border-bottom: 2px solid #e2e8f0; margin-bottom: 1.5rem; }
    .tabs-bar button { padding: 0.75rem 1rem; font-weight: 600; border: none; background: none; cursor: pointer; color: #64748b; }
    .tabs-bar button.active { color: #0284c7; border-bottom: 2px solid #0284c7; margin-bottom: -2px; }
    .section-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.5rem; margin-bottom: 1.5rem; }
    .filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .filter-group { display: flex; align-items: center; gap: 0.5rem; }
    .ir-input { border: 1px solid #cbd5e1; border-radius: 6px; padding: 0.5rem 0.75rem; }
    .ir-table-wrapper { overflow-x: auto; }
    .ir-table { width: 100%; border-collapse: collapse; }
    .ir-table th, .ir-table td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #e2e8f0; }
    .badge { padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-warn { background: #fef3c7; color: #b45309; }
    .badge-danger { background: #fee2e2; color: #b91c1c; }
    .badge-success { background: #dcfce7; color: #15803d; }
    .action-buttons { display: flex; gap: 0.5rem; }
    .ir-modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 100; }
    .ir-modal { background: #fff; border-radius: 8px; padding: 1.5rem; width: 100%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
    .ir-modal-lg { max-width: 800px; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.5rem; }
    .ir-alert { padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; }
    .ir-alert-error { background: #fee2e2; color: #b91c1c; }
    .ir-alert-success { background: #dcfce7; color: #15803d; }
    .checkbox-row { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.75rem; }
  `]
})
export class AdminBookingsComponent implements OnInit {
  private readonly adminService = inject(AdminBookingFinanceService);

  activeTab = signal<'bookings' | 'exceptions'>('bookings');
  loading = signal(false);
  sweepLoading = signal(false);
  actionLoading = signal(false);
  alert = signal<{ type: 'error' | 'success'; message: string } | null>(null);

  bookings = signal<BookingSummaryDto[]>([]);
  filteredBookings = signal<BookingSummaryDto[]>([]);
  totalBookings = signal(0);
  statusFilter = 'ALL';

  exceptions = signal<OverbookingExceptionDto[]>([]);

  selectedBooking = signal<BookingDetailDto | null>(null);

  showDisruptionModal = signal(false);
  targetDisruptionBooking: BookingSummaryDto | null = null;
  disruptionForm = {
    type: 'WEATHER_DISRUPTION' as DisruptionExceptionType,
    reason: '',
    preferReschedule: false,
  };

  showDisputeModal = signal(false);
  targetDisputeBooking: BookingSummaryDto | null = null;
  disputeForm = {
    targetStatus: 'COMPLETED' as BookingStatus,
    refundAmount: null as number | null,
    depositForfeitAmount: null as number | null,
    resolutionNotes: '',
  };

  showExceptionModal = signal(false);
  targetException: OverbookingExceptionDto | null = null;
  exceptionResolutionNotes = '';

  ngOnInit(): void {
    this.loadBookings();
    this.loadExceptions();
  }

  loadBookings(): void {
    this.loading.set(true);
    this.adminService.listBookings(0, 50).subscribe({
      next: (page) => {
        this.bookings.set(page.content);
        this.totalBookings.set(page.totalElements);
        this.filterBookings();
        this.loading.set(false);
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to load bookings' });
        this.loading.set(false);
      },
    });
  }

  loadExceptions(): void {
    this.adminService.listOverbookingExceptions().subscribe({
      next: (list) => this.exceptions.set(list),
      error: () => {},
    });
  }

  filterBookings(): void {
    if (this.statusFilter === 'ALL') {
      this.filteredBookings.set(this.bookings());
    } else {
      this.filteredBookings.set(this.bookings().filter((b) => b.status === this.statusFilter));
    }
  }

  triggerExpirationSweep(): void {
    this.sweepLoading.set(true);
    this.adminService.triggerBookingExpirationSweep().subscribe({
      next: (res) => {
        this.alert.set({ type: 'success', message: `Sweep complete. Expired ${res.expiredBookingsCount} bookings.` });
        this.sweepLoading.set(false);
        this.loadBookings();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to trigger sweep' });
        this.sweepLoading.set(false);
      },
    });
  }

  openDetail(bookingPublicId: string): void {
    this.adminService.getBooking(bookingPublicId).subscribe({
      next: (d) => this.selectedBooking.set(d),
      error: (err) => this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to get details' }),
    });
  }

  openDisruptionModal(b: BookingSummaryDto): void {
    this.targetDisruptionBooking = b;
    this.disruptionForm = {
      type: 'WEATHER_DISRUPTION' as DisruptionExceptionType,
      reason: '',
      preferReschedule: false,
    };
    this.showDisruptionModal.set(true);
  }

  executeDisruptionOverride(): void {
    if (!this.targetDisruptionBooking) return;
    this.actionLoading.set(true);
    this.adminService.applyDisruptionOverride(this.targetDisruptionBooking.publicId, {
      disruptionType: this.disruptionForm.type,
      reason: this.disruptionForm.reason,
      preferReschedule: this.disruptionForm.preferReschedule,
    }).subscribe({
      next: () => {
        this.alert.set({ type: 'success', message: 'Disruption override applied successfully.' });
        this.actionLoading.set(false);
        this.showDisruptionModal.set(false);
        this.loadBookings();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to apply disruption override' });
        this.actionLoading.set(false);
      },
    });
  }

  openDisputeModal(b: BookingSummaryDto): void {
    this.targetDisputeBooking = b;
    this.disputeForm = {
      targetStatus: 'COMPLETED' as BookingStatus,
      refundAmount: null,
      depositForfeitAmount: null,
      resolutionNotes: '',
    };
    this.showDisputeModal.set(true);
  }

  executeDisputeResolution(): void {
    if (!this.targetDisputeBooking) return;
    this.actionLoading.set(true);
    this.adminService.resolveDispute(this.targetDisputeBooking.publicId, {
      targetStatus: this.disputeForm.targetStatus,
      resolutionNotes: this.disputeForm.resolutionNotes,
      refundAmount: this.disputeForm.refundAmount,
      depositForfeitAmount: this.disputeForm.depositForfeitAmount,
    }).subscribe({
      next: () => {
        this.alert.set({ type: 'success', message: 'Dispute resolved successfully.' });
        this.actionLoading.set(false);
        this.showDisputeModal.set(false);
        this.loadBookings();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to resolve dispute' });
        this.actionLoading.set(false);
      },
    });
  }

  openResolveExceptionModal(ex: OverbookingExceptionDto): void {
    this.targetException = ex;
    this.exceptionResolutionNotes = '';
    this.showExceptionModal.set(true);
  }

  executeResolveException(): void {
    if (!this.targetException) return;
    this.actionLoading.set(true);
    this.adminService.resolveOverbookingException(this.targetException.publicId, this.exceptionResolutionNotes).subscribe({
      next: () => {
        this.alert.set({ type: 'success', message: 'Overbooking exception marked as resolved.' });
        this.actionLoading.set(false);
        this.showExceptionModal.set(false);
        this.loadExceptions();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to resolve exception' });
        this.actionLoading.set(false);
      },
    });
  }

  getStatusBadge(status: BookingStatus): string {
    switch (status) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'badge-success';
      case 'AWAITING_PARTNER_APPROVAL':
      case 'APPROVED':
        return 'badge-warn';
      case 'ACTIVE':
        return 'badge-info';
      case 'CANCELLED':
      case 'REJECTED':
      case 'EXPIRED':
      case 'NO_SHOW':
      case 'DISPUTED':
        return 'badge-danger';
      default:
        return 'badge-info';
    }
  }
}
