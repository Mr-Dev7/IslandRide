import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminBookingFinanceService } from '../../core/services/admin-booking-finance.service';
import {
  AdminFinanceOverviewDto,
  PaymentSummaryDto,
  RefundStatus,
  RefundSummaryDto,
  RefundType,
} from '../../core/models/booking.models';

@Component({
  selector: 'app-admin-finance',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Finance Governance & Trust Ledger</h1>
          <p class="muted">Monitor platform commission, ring-fenced security deposit trust liabilities, reconciliations, and payment gateways.</p>
        </div>
        <div class="head-actions">
          <button (click)="triggerReconciliation()" [disabled]="reconcileLoading()" class="ir-btn ir-btn-outline">
            {{ reconcileLoading() ? 'Reconciling...' : 'Reconcile Stale Payments' }}
          </button>
          <button (click)="openManualRefundModal()" class="ir-btn ir-btn-primary">Issue Manual Refund</button>
        </div>
      </div>

      @if (alert()) {
        <div class="ir-alert" [ngClass]="alert()!.type === 'error' ? 'ir-alert-error' : 'ir-alert-success'">
          {{ alert()!.message }}
        </div>
      }

      @if (loading()) {
        <p class="muted text-center py-8">Loading financial overview...</p>
      } @else if (overview()) {
        <!-- Key Metrics Cards -->
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-label">Online Rentals Captured</div>
            <div class="kpi-val val-teal">₹{{ overview()!.totalOnlineRentalCaptured | number:'1.2-2' }}</div>
            <div class="kpi-sub">Razorpay verified captures</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Offline Rentals Recorded</div>
            <div class="kpi-val val-navy">₹{{ overview()!.totalOfflineRentalRecorded | number:'1.2-2' }}</div>
            <div class="kpi-sub">On-counter verified bookings</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Platform Commission (Net)</div>
            <div class="kpi-val val-navy">₹{{ overview()!.totalPlatformCommission | number:'1.2-2' }}</div>
            <div class="kpi-sub">Strictly excludes deposits & GST</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Completed Refunds</div>
            <div class="kpi-val val-muted">₹{{ overview()!.totalCompletedRefundAmount | number:'1.2-2' }}</div>
            <div class="kpi-sub">Pending: ₹{{ overview()!.totalPendingRefundAmount | number:'1.2-2' }}</div>
          </div>
        </div>

        <!-- Security Deposit Trust Account -->
        <div class="trust-card">
          <div class="trust-header">
            <div>
              <div class="fiduciary-tag">Statutory Fiduciary Escrow</div>
              <h2>Ring-Fenced Security Deposit Trust Account</h2>
              <p class="trust-sub">
                Security deposits are strictly ring-fenced from operational income and platform commissions.
              </p>
            </div>
            <span class="badge badge-info">Fiduciary Protection Active</span>
          </div>
          <div class="trust-grid">
            <div class="trust-stat">
              <span class="trust-stat-label">Active Held Liability</span>
              <strong class="trust-stat-val">₹{{ overview()!.depositTrustSummary.totalCollectedHeldLiability | number:'1.2-2' }}</strong>
              <span class="trust-stat-sub">({{ overview()!.depositTrustSummary.activeHeldCount }} active deposits)</span>
            </div>
            <div class="trust-stat">
              <span class="trust-stat-label">Under Inspection Hold</span>
              <strong class="trust-stat-val val-warn">₹{{ overview()!.depositTrustSummary.totalInspectionHoldAmount | number:'1.2-2' }}</strong>
              <span class="trust-stat-sub">({{ overview()!.depositTrustSummary.inspectionHoldCount }} pending claims)</span>
            </div>
            <div class="trust-stat">
              <span class="trust-stat-label">Released to Tourists</span>
              <strong class="trust-stat-val val-teal">₹{{ overview()!.depositTrustSummary.totalReleasedDeposits | number:'1.2-2' }}</strong>
              <span class="trust-stat-sub">Returned post-inspection</span>
            </div>
            <div class="trust-stat">
              <span class="trust-stat-label">Forfeited (Damage/Penalty)</span>
              <strong class="trust-stat-val val-coral">₹{{ overview()!.depositTrustSummary.totalForfeitedDeposits | number:'1.2-2' }}</strong>
              <span class="trust-stat-sub">Transferred to vendor claims</span>
            </div>
          </div>
        </div>

        <!-- Tabs: Recent Transactions -->
        <div class="tabs-bar">
          <button [class.active]="activeTab() === 'refunds'" (click)="activeTab.set('refunds')">
            Refunds & Retries ({{ overview()!.recentRefunds.length }})
            @if (overview()!.failedRefundsCount > 0) {
              <span class="tab-badge-danger">{{ overview()!.failedRefundsCount }} failed</span>
            }
          </button>
          <button [class.active]="activeTab() === 'payments'" (click)="activeTab.set('payments')">
            Recent Payments ({{ overview()!.recentPayments.length }})
          </button>
          <button [class.active]="activeTab() === 'webhooks'" (click)="activeTab.set('webhooks')">
            Webhook Logs ({{ overview()!.recentWebhooks.length }})
          </button>
        </div>

        <!-- Tab: Refunds -->
        @if (activeTab() === 'refunds') {
          <div class="section-card">
            <h3>Refunds Orchestration Ledger</h3>
            <div class="ir-table-wrapper mt-3">
              <table class="ir-table">
                <thead>
                  <tr>
                    <th>Created</th>
                    <th>Booking / Refund ID</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Attempts</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of overview()!.recentRefunds; track r.publicId) {
                    <tr>
                      <td class="text-xs">{{ r.createdAt | date:'short' }}</td>
                      <td>
                        <strong>{{ r.publicId }}</strong>
                        <div class="text-xs muted">Booking: {{ r.bookingPublicId }}</div>
                      </td>
                      <td><span class="badge badge-info">{{ r.refundType }}</span></td>
                      <td><strong>₹{{ r.amount | number:'1.2-2' }}</strong></td>
                      <td>
                        <span class="badge" [ngClass]="getRefundBadge(r.status)">{{ r.status }}</span>
                        @if (r.errorMessage) {
                          <div class="text-xs error-sub mt-1">{{ r.errorMessage }}</div>
                        }
                      </td>
                      <td class="text-xs">{{ r.retryCount }}</td>
                      <td>
                        @if (r.status === 'FAILED' || r.status === 'PENDING') {
                          <button (click)="retryRefund(r.publicId)" [disabled]="retryingRefundId() === r.publicId" class="ir-btn ir-btn-sm ir-btn-primary">
                            {{ retryingRefundId() === r.publicId ? 'Retrying...' : 'Retry' }}
                          </button>
                        } @else {
                          <span class="text-xs muted">{{ r.gatewayRefundId || 'Settled' }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <!-- Tab: Payments -->
        @if (activeTab() === 'payments') {
          <div class="section-card">
            <h3>Recent Captured & Recorded Payments</h3>
            <div class="ir-table-wrapper mt-3">
              <table class="ir-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Booking</th>
                    <th>Type</th>
                    <th>Method</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Gateway Ref</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of overview()!.recentPayments; track p.publicId) {
                    <tr>
                      <td class="text-xs">{{ p.createdAt | date:'short' }}</td>
                      <td class="text-xs">{{ p.bookingPublicId }}</td>
                      <td>{{ p.paymentType }}</td>
                      <td>{{ p.paymentMethod }}</td>
                      <td><strong>₹{{ p.amount | number:'1.2-2' }}</strong></td>
                      <td><span class="badge badge-success">{{ p.status }}</span></td>
                      <td class="text-xs muted">{{ p.gatewayOrderId || p.gatewayPaymentId || '-' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <!-- Tab: Webhooks -->
        @if (activeTab() === 'webhooks') {
          <div class="section-card">
            <h3>Payment Gateway Webhook Ledger</h3>
            <div class="ir-table-wrapper mt-3">
              <table class="ir-table">
                <thead>
                  <tr>
                    <th>Received At</th>
                    <th>Provider</th>
                    <th>Event Type</th>
                    <th>Gateway Event ID</th>
                    <th>Signature</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  @for (w of overview()!.recentWebhooks; track w.publicId) {
                    <tr>
                      <td class="text-xs">{{ w.receivedAt | date:'short' }}</td>
                      <td><strong>{{ w.gatewayProvider }}</strong></td>
                      <td>{{ w.eventType }}</td>
                      <td class="text-xs muted">{{ w.gatewayEventId }}</td>
                      <td>
                        <span class="badge" [ngClass]="w.signatureVerified ? 'badge-success' : 'badge-danger'">
                          {{ w.signatureVerified ? 'VERIFIED' : 'FAILED' }}
                        </span>
                      </td>
                      <td><span class="badge badge-info">{{ w.processingStatus }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      }

      <!-- Modal: Manual Refund -->
      @if (showManualRefundModal()) {
        <div class="ir-modal-backdrop">
          <div class="ir-modal">
            <h3>Issue Manual Refund</h3>
            <p class="muted text-sm">Issue an authoritative administrative refund for a booking.</p>
            <div class="ir-form-group mt-4">
              <label for="refundBkgId">Booking Public ID</label>
              <input id="refundBkgId" type="text" [(ngModel)]="manualRefundForm.bookingPublicId" class="ir-input" placeholder="bkg_..." />
            </div>
            <div class="ir-form-group">
              <label for="refundAmount">Refund Amount (₹)</label>
              <input id="refundAmount" type="number" [(ngModel)]="manualRefundForm.amount" class="ir-input" placeholder="0.00" />
            </div>
            <div class="ir-form-group">
              <label for="refundType">Refund Type</label>
              <select id="refundType" [(ngModel)]="manualRefundForm.type" class="ir-input">
                <option value="MANUAL">MANUAL (Admin)</option>
                <option value="DISRUPTION">DISRUPTION</option>
                <option value="SECURITY_DEPOSIT">SECURITY_DEPOSIT</option>
                <option value="PARTIAL_ADJUSTMENT">PARTIAL_ADJUSTMENT</option>
              </select>
            </div>
            <div class="ir-form-group">
              <label for="refundReason">Reason / Audit Note</label>
              <textarea id="refundReason" [(ngModel)]="manualRefundForm.reason" rows="3" class="ir-input" placeholder="State reason for manual refund."></textarea>
            </div>
            <div class="modal-actions">
              <button (click)="showManualRefundModal.set(false)" class="ir-btn ir-btn-outline">Cancel</button>
              <button (click)="executeManualRefund()" [disabled]="actionLoading() || !manualRefundForm.bookingPublicId || !manualRefundForm.amount || !manualRefundForm.reason" class="ir-btn ir-btn-primary">
                {{ actionLoading() ? 'Issuing...' : 'Issue Refund' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .ir-page {
      padding: 1.5rem;
      max-width: 1200px;
      margin: 0 auto;
    }
    .page-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .page-head h1 {
      font-size: 1.625rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.25rem 0;
    }
    .head-actions {
      display: flex;
      gap: 0.75rem;
      align-items: center;
      flex-wrap: wrap;
    }
    .muted {
      color: var(--ir-muted);
      font-size: 0.875rem;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    .kpi-card {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      padding: 1.25rem;
      box-shadow: var(--ir-shadow-sm);
    }
    .kpi-label {
      font-size: 0.75rem;
      color: var(--ir-muted);
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin-bottom: 0.5rem;
    }
    .kpi-val {
      font-size: 1.5rem;
      font-weight: 700;
      line-height: 1.2;
    }
    .val-teal { color: var(--ir-teal); }
    .val-navy { color: var(--ir-navy); }
    .val-muted { color: var(--ir-charcoal); }
    .val-warn { color: #d97706; }
    .val-coral { color: var(--ir-coral); }
    .kpi-sub {
      font-size: 0.75rem;
      color: var(--ir-muted);
      margin-top: 0.35rem;
    }

    /* Trust Card */
    .trust-card {
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-md);
      padding: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .trust-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1rem;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .fiduciary-tag {
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--ir-teal);
      margin-bottom: 0.25rem;
    }
    .trust-header h2 {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.25rem 0;
    }
    .trust-sub {
      font-size: 0.8125rem;
      color: var(--ir-charcoal);
      margin: 0;
      line-height: 1.4;
    }
    .trust-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 1rem;
      margin-top: 1rem;
    }
    @media (max-width: 800px) {
      .trust-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    @media (max-width: 480px) {
      .trust-grid {
        grid-template-columns: 1fr;
      }
    }
    .trust-stat {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-sm);
      padding: 0.875rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .trust-stat-label {
      font-size: 0.75rem;
      color: var(--ir-muted);
      font-weight: 600;
    }
    .trust-stat-val {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--ir-navy);
    }
    .trust-stat-sub {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }

    .tabs-bar {
      display: flex;
      gap: 0.5rem;
      border-bottom: 2px solid var(--ir-border);
      margin-bottom: 1.5rem;
      overflow-x: auto;
    }
    .tabs-bar button {
      padding: 0.75rem 1.25rem;
      font-weight: 600;
      border: none;
      background: none;
      cursor: pointer;
      color: var(--ir-muted);
      font-size: 0.9375rem;
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      transition: color var(--ir-transition), border-color var(--ir-transition);
      display: flex;
      align-items: center;
      gap: 0.5rem;
      white-space: nowrap;
    }
    .tabs-bar button:hover {
      color: var(--ir-navy);
    }
    .tabs-bar button.active {
      color: var(--ir-teal);
      border-bottom-color: var(--ir-teal);
    }
    .tab-badge-danger {
      background: #fee2e2;
      color: #b91c1c;
      font-size: 0.7rem;
      padding: 0.15rem 0.4rem;
      border-radius: 9999px;
    }
    .section-card {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      padding: 1.5rem;
      margin-bottom: 1.5rem;
      box-shadow: var(--ir-shadow-sm);
    }
    .section-card h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0;
    }
    .ir-table-wrapper {
      overflow-x: auto;
    }
    .ir-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
    }
    .ir-table th {
      padding: 0.75rem;
      text-align: left;
      font-weight: 600;
      color: var(--ir-navy);
      background: var(--ir-sea-mist);
      border-bottom: 1px solid var(--ir-border);
    }
    .ir-table td {
      padding: 0.75rem;
      text-align: left;
      border-bottom: 1px solid var(--ir-border);
      color: var(--ir-charcoal);
    }
    .ir-table tr:hover td {
      background: #f8fafc;
    }
    .badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: var(--ir-radius-sm);
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.02em;
    }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-warn { background: #fef3c7; color: #b45309; }
    .badge-danger { background: #fee2e2; color: #b91c1c; }
    .badge-success { background: #dcfce7; color: #15803d; }
    .error-sub {
      color: #b91c1c;
      font-weight: 600;
    }

    .ir-input {
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-sm);
      padding: 0.5rem 0.75rem;
      font-size: 0.875rem;
      background: #ffffff;
      color: var(--ir-charcoal);
      width: 100%;
    }
    .ir-input:focus {
      outline: none;
      border-color: var(--ir-teal);
      box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.15);
    }
    .ir-modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(18, 59, 74, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }
    .ir-modal {
      background: var(--ir-surface);
      border-radius: var(--ir-radius-lg);
      padding: 1.5rem;
      width: 100%;
      max-width: 520px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: var(--ir-shadow-lg);
      border: 1px solid var(--ir-border);
    }
    .ir-modal h3 {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.35rem 0;
    }
    .ir-form-group {
      margin-bottom: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .ir-form-group label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--ir-charcoal);
    }
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid var(--ir-border);
    }
    .text-xs { font-size: 0.75rem; }
    .text-sm { font-size: 0.875rem; }
    .text-center { text-align: center; }
    .py-8 { padding-top: 2rem; padding-bottom: 2rem; }
    .mt-1 { margin-top: 0.25rem; }
    .mt-3 { margin-top: 0.75rem; }
    .mt-4 { margin-top: 1rem; }
  `]
})
export class AdminFinanceComponent implements OnInit {
  private readonly adminService = inject(AdminBookingFinanceService);

  loading = signal(false);
  reconcileLoading = signal(false);
  actionLoading = signal(false);
  retryingRefundId = signal<string | null>(null);
  alert = signal<{ type: 'error' | 'success'; message: string } | null>(null);

  overview = signal<AdminFinanceOverviewDto | null>(null);
  activeTab = signal<'refunds' | 'payments' | 'webhooks'>('refunds');

  showManualRefundModal = signal(false);
  manualRefundForm = {
    bookingPublicId: '',
    amount: null as number | null,
    type: 'MANUAL' as RefundType,
    reason: '',
  };

  ngOnInit(): void {
    this.loadOverview();
  }

  loadOverview(): void {
    this.loading.set(true);
    this.adminService.getFinanceOverview().subscribe({
      next: (data) => {
        this.overview.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to load financial overview' });
        this.loading.set(false);
      },
    });
  }

  triggerReconciliation(): void {
    this.reconcileLoading.set(true);
    this.adminService.triggerPaymentReconciliation().subscribe({
      next: (res) => {
        this.alert.set({ type: 'success', message: `Reconciliation finished. Reconciled ${res.reconciledPaymentsCount} stale payments.` });
        this.reconcileLoading.set(false);
        this.loadOverview();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Reconciliation failed' });
        this.reconcileLoading.set(false);
      },
    });
  }

  retryRefund(refundPublicId: string): void {
    this.retryingRefundId.set(refundPublicId);
    this.adminService.retryRefund(refundPublicId, false).subscribe({
      next: () => {
        this.alert.set({ type: 'success', message: `Refund retry initiated for ${refundPublicId}.` });
        this.retryingRefundId.set(null);
        this.loadOverview();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to retry refund' });
        this.retryingRefundId.set(null);
      },
    });
  }

  openManualRefundModal(): void {
    this.manualRefundForm = {
      bookingPublicId: '',
      amount: null,
      type: 'MANUAL',
      reason: '',
    };
    this.showManualRefundModal.set(true);
  }

  executeManualRefund(): void {
    if (!this.manualRefundForm.bookingPublicId || !this.manualRefundForm.amount || !this.manualRefundForm.reason) return;
    this.actionLoading.set(true);
    const idempKey = 'adm_ref_' + Date.now();
    this.adminService.issueManualRefund(
      this.manualRefundForm.bookingPublicId,
      {
        amount: this.manualRefundForm.amount,
        reason: this.manualRefundForm.reason,
        refundType: this.manualRefundForm.type,
      },
      idempKey
    ).subscribe({
      next: () => {
        this.alert.set({ type: 'success', message: 'Manual refund recorded successfully.' });
        this.actionLoading.set(false);
        this.showManualRefundModal.set(false);
        this.loadOverview();
      },
      error: (err) => {
        this.alert.set({ type: 'error', message: err?.error?.message || 'Failed to issue manual refund' });
        this.actionLoading.set(false);
      },
    });
  }

  getRefundBadge(status: RefundStatus): string {
    switch (status) {
      case 'SUCCESS':
        return 'badge-success';
      case 'PENDING':
      case 'PROCESSING':
        return 'badge-warn';
      case 'FAILED':
        return 'badge-danger';
      default:
        return 'badge-info';
    }
  }
}
