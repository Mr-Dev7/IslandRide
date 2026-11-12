import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { PartnerFinancialService } from '../../core/services/partner-financial.service';
import { PartnerFinancialOverviewDto } from '../../core/models/booking.models';

@Component({
  selector: 'app-partner-financials',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe],
  template: `
    <div class="financials-container">
      <div class="header-row">
        <div>
          <h2>Partner Financials & Earnings</h2>
          <p class="subtitle" *ngIf="overview()">Business: {{ overview()?.tradeName }}</p>
        </div>
        <button class="btn btn-secondary" (click)="loadOverview()" [disabled]="loading()">
          {{ loading() ? 'Refreshing...' : 'Refresh' }}
        </button>
      </div>

      <div *ngIf="errorMessage()" class="alert alert-error">
        {{ errorMessage() }}
      </div>

      <div *ngIf="overview() as data" class="dashboard-content">
        <!-- KPI Cards Grid -->
        <div class="kpi-grid">
          <div class="card kpi-card">
            <span class="kpi-label">Gross Rental Volume</span>
            <span class="kpi-value text-primary">{{ data.totalGrossBookings | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">{{ data.totalCompletedBookings }} completed bookings</span>
          </div>

          <div class="card kpi-card">
            <span class="kpi-label">Platform Commission</span>
            <span class="kpi-value text-secondary">{{ data.totalCommissionDeducted | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">Accrued platform fees</span>
          </div>

          <div class="card kpi-card highlight">
            <span class="kpi-label">Net Partner Earnings</span>
            <span class="kpi-value text-success">{{ data.totalNetEarnings | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">Total earned post-commission</span>
          </div>

          <div class="card kpi-card">
            <span class="kpi-label">Pending Payout</span>
            <span class="kpi-value text-accent">{{ data.pendingSettlementBalance | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">Unsettled earnings balance</span>
          </div>

          <div class="card kpi-card">
            <span class="kpi-label">Total Paid Out</span>
            <span class="kpi-value text-muted">{{ data.totalSettledPaidOut | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">Completed bank transfers</span>
          </div>

          <div class="card kpi-card">
            <span class="kpi-label">Open Receivables</span>
            <span class="kpi-value text-warning">{{ data.openReceivablesBalance | currency:'INR':'symbol':'1.2-2' }}</span>
            <span class="kpi-sub">Excess offline offsets</span>
          </div>
        </div>

        <!-- Collections Breakdown -->
        <div class="card collections-card">
          <h3>Collection Channels Breakdown</h3>
          <div class="channels-grid">
            <div class="channel-item">
              <span class="channel-label">Online Captured (Platform Gateway):</span>
              <span class="channel-val">{{ data.onlineCollectedByPlatform | currency:'INR':'symbol':'1.2-2' }}</span>
            </div>
            <div class="channel-item">
              <span class="channel-label">Offline Collected (In-Person Cash/UPI):</span>
              <span class="channel-val">{{ data.offlineCollectedByPartner | currency:'INR':'symbol':'1.2-2' }}</span>
            </div>
          </div>
        </div>

        <!-- Commissions Ledger Table -->
        <div class="card table-card">
          <div class="card-header">
            <h3>Recent Booking Commission Statements</h3>
          </div>
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Booking Ref</th>
                  <th>Vehicle</th>
                  <th>Gross Base</th>
                  <th>Commission Rate</th>
                  <th>Commission Amount</th>
                  <th>Net Earnings</th>
                  <th>Status</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let comm of data.recentCommissions">
                  <td class="font-mono">{{ comm.bookingReference || '—' }}</td>
                  <td>{{ comm.vehicleTitle || '—' }}</td>
                  <td>{{ comm.grossRentalAmount | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td>{{ comm.commissionPercentage }}%</td>
                  <td class="text-danger">-{{ comm.commissionAmount | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td class="text-success font-semibold">{{ comm.partnerEarnings | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td><span class="badge" [ngClass]="comm.status.toLowerCase()">{{ comm.status }}</span></td>
                  <td>{{ comm.createdAt | date:'short' }}</td>
                </tr>
                <tr *ngIf="data.recentCommissions.length === 0">
                  <td colspan="8" class="text-center text-muted">No completed bookings or commissions recorded yet.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Settlement History Table -->
        <div class="card table-card">
          <div class="card-header">
            <h3>Payout & Settlement History</h3>
          </div>
          <div class="table-responsive">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Settlement ID</th>
                  <th>Period</th>
                  <th>Gross Total</th>
                  <th>Commission Deducted</th>
                  <th>Net Payout</th>
                  <th>Bank UTR Reference</th>
                  <th>Status</th>
                  <th>Processed Date</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let stl of data.recentSettlements">
                  <td class="font-mono">{{ stl.publicId }}</td>
                  <td>{{ stl.periodStart | date:'mediumDate' }} - {{ stl.periodEnd | date:'mediumDate' }}</td>
                  <td>{{ stl.grossAmount | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td>-{{ stl.commissionDeducted | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td class="text-success font-semibold">{{ stl.netPayoutAmount | currency:'INR':'symbol':'1.2-2' }}</td>
                  <td><span class="font-mono text-muted">{{ stl.bankUtrReference || 'Pending UTR' }}</span></td>
                  <td><span class="badge" [ngClass]="stl.status.toLowerCase()">{{ stl.status }}</span></td>
                  <td>{{ (stl.processedAt | date:'short') || 'In Process' }}</td>
                </tr>
                <tr *ngIf="data.recentSettlements.length === 0">
                  <td colspan="8" class="text-center text-muted">No payout settlements recorded yet.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .financials-container { max-width: 1200px; margin: 0 auto; padding: 1.5rem; }
    .header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .subtitle { color: #64748b; font-size: 0.95rem; margin-top: 0.25rem; }
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .card { background: #fff; border-radius: 8px; border: 1px solid #e2e8f0; padding: 1.25rem; }
    .kpi-card { display: flex; flex-direction: column; gap: 0.35rem; }
    .kpi-card.highlight { border-color: #10b981; background: #f0fdf4; }
    .kpi-label { font-size: 0.8rem; font-weight: 600; text-transform: uppercase; color: #64748b; }
    .kpi-value { font-size: 1.5rem; font-weight: 700; }
    .kpi-sub { font-size: 0.75rem; color: #94a3b8; }
    .text-primary { color: #0284c7; }
    .text-secondary { color: #6366f1; }
    .text-success { color: #059669; }
    .text-accent { color: #d97706; }
    .text-warning { color: #dc2626; }
    .text-muted { color: #64748b; }
    .text-danger { color: #ef4444; }
    .font-mono { font-family: monospace; }
    .font-semibold { font-weight: 600; }
    .collections-card { margin-bottom: 1.5rem; }
    .channels-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 0.75rem; }
    .channel-item { display: flex; justify-content: space-between; padding: 0.5rem; background: #f8fafc; border-radius: 6px; }
    .channel-label { color: #475569; font-size: 0.9rem; }
    .channel-val { font-weight: 600; color: #0f172a; }
    .table-card { margin-bottom: 1.5rem; padding: 0; overflow: hidden; }
    .card-header { padding: 1rem 1.25rem; border-bottom: 1px solid #e2e8f0; background: #f8fafc; }
    .table-responsive { overflow-x: auto; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    .data-table th, .data-table td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #f1f5f9; }
    .data-table th { background: #f8fafc; font-weight: 600; color: #475569; font-size: 0.8rem; text-transform: uppercase; }
    .badge { display: inline-block; padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }
    .badge.accrued, .badge.initiated { background: #fef3c7; color: #b45309; }
    .badge.settled, .badge.completed { background: #d1fae5; color: #065f46; }
    .badge.processing { background: #e0e7ff; color: #3730a3; }
    .badge.voided, .badge.failed { background: #fee2e2; color: #991b1b; }
    .btn { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 600; cursor: pointer; border: none; }
    .btn-secondary { background: #f1f5f9; color: #334155; }
    .btn-secondary:hover { background: #e2e8f0; }
    .alert-error { background: #fee2e2; color: #991b1b; padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; }
  `]
})
export class PartnerFinancialsComponent implements OnInit {
  private readonly financialService = inject(PartnerFinancialService);

  readonly overview = signal<PartnerFinancialOverviewDto | null>(null);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadOverview();
  }

  loadOverview(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.financialService.getFinancialOverview().subscribe({
      next: (data) => {
        this.overview.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message || 'Failed to load partner financial overview.');
        this.loading.set(false);
      }
    });
  }
}

