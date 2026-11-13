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
          <p class="subtitle" *ngIf="overview()">Business: {{ overview()?.tradeName }} | Settlement Cycle: {{ overview()?.settlementCycleDays ?? 7 }}d (Configurable Default) | Auto-Offset: {{ overview()?.autoOffsetReceivables ? 'Active' : 'Disabled' }}</p>
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
    .header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; gap: 1rem; flex-wrap: wrap; }
    .header-row h2 { font-size: 1.625rem; font-weight: 700; color: var(--ir-navy); margin: 0; }
    .subtitle { color: var(--ir-muted); font-size: 0.875rem; margin-top: 0.25rem; }
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .card { background: var(--ir-surface); border-radius: var(--ir-radius-md); border: 1px solid var(--ir-border); padding: 1.25rem; box-shadow: var(--ir-shadow-sm); }
    .kpi-card { display: flex; flex-direction: column; gap: 0.35rem; }
    .kpi-card.highlight { border-color: var(--ir-teal); background: var(--ir-sea-mist); }
    .kpi-label { font-size: 0.75rem; font-weight: 700; text-transform: uppercase; color: var(--ir-muted); letter-spacing: 0.04em; }
    .kpi-value { font-size: 1.5rem; font-weight: 700; line-height: 1.2; }
    .kpi-sub { font-size: 0.75rem; color: var(--ir-muted); }
    .text-primary { color: var(--ir-navy); }
    .text-secondary { color: var(--ir-charcoal); }
    .text-success { color: var(--ir-teal); }
    .text-accent { color: #d97706; }
    .text-warning { color: #b45309; }
    .text-muted { color: var(--ir-muted); }
    .text-danger { color: var(--ir-coral); }
    .font-mono { font-family: monospace; font-size: 0.8125rem; }
    .font-semibold { font-weight: 600; }
    .collections-card { margin-bottom: 1.5rem; }
    .collections-card h3 { font-size: 1.125rem; font-weight: 700; color: var(--ir-navy); margin: 0 0 0.5rem 0; }
    .channels-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 0.75rem; }
    @media (max-width: 600px) { .channels-grid { grid-template-columns: 1fr; } }
    .channel-item { display: flex; justify-content: space-between; padding: 0.75rem 1rem; background: var(--ir-sea-mist); border-radius: var(--ir-radius-sm); border: 1px solid #c2e0d8; }
    .channel-label { color: var(--ir-charcoal); font-size: 0.875rem; font-weight: 500; }
    .channel-val { font-weight: 700; color: var(--ir-navy); }
    .table-card { margin-bottom: 1.5rem; padding: 0; overflow: hidden; }
    .card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--ir-border); background: var(--ir-sea-mist); }
    .card-header h3 { font-size: 1.05rem; font-weight: 700; color: var(--ir-navy); margin: 0; }
    .table-responsive { overflow-x: auto; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    .data-table th, .data-table td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid var(--ir-border); }
    .data-table th { background: var(--ir-sea-mist); font-weight: 600; color: var(--ir-navy); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.03em; }
    .data-table tr:hover td { background: #f8fafc; }
    .badge { display: inline-block; padding: 0.2rem 0.5rem; border-radius: var(--ir-radius-sm); font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }
    .badge.accrued, .badge.initiated { background: #fef3c7; color: #b45309; }
    .badge.settled, .badge.completed { background: #d1fae5; color: #065f46; }
    .badge.processing { background: #e0f2fe; color: #0369a1; }
    .badge.voided, .badge.failed { background: #fee2e2; color: #991b1b; }
    .btn { padding: 0.5rem 1rem; border-radius: var(--ir-radius-sm); font-weight: 600; cursor: pointer; border: none; min-height: 40px; display: inline-flex; align-items: center; justify-content: center; }
    .btn-secondary { background: #ffffff; color: var(--ir-charcoal); border: 1px solid var(--ir-border); }
    .btn-secondary:hover { background: #f1f5f9; border-color: #cbd5e1; }
    .alert-error { background: #fee2e2; color: #991b1b; padding: 0.75rem 1rem; border-radius: var(--ir-radius-sm); margin-bottom: 1rem; }
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

