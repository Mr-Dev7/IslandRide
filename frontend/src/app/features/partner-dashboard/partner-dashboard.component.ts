import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PartnerInventoryService } from '../../core/services/partner-inventory.service';
import { PartnerInventorySummaryDto } from '../../core/models/vehicle.models';

@Component({
  selector: 'app-partner-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard-page">
      <div class="ir-card pad header-card">
        <div class="page-head">
          <div>
            <h2>Partner Operations Console</h2>
            <p class="muted">Manage your Andaman & Nicobar fleet inventory, pricing plans, documents, and availability blocks.</p>
          </div>
          <div class="actions-row">
            <a routerLink="/partner/fleet" class="ir-btn ir-btn-primary">Manage Fleet</a>
            @if (canWrite()) {
              <a routerLink="/partner/fleet/new" class="ir-btn ir-btn-outline">+ Add Vehicle</a>
            }
            <a routerLink="/partner/calendar" class="ir-btn ir-btn-outline">Calendar & Hubs</a>
          </div>
        </div>

        @if (summary(); as s) {
          <div class="kpi-grid">
            <div class="kpi-card">
              <span class="kpi-label">Business Status</span>
              <span class="kpi-value" [class.status-approved]="s.businessVerificationStatus === 'APPROVED'">{{ s.businessVerificationStatus }}</span>
              <span class="kpi-sub">Verified Vendor Account</span>
            </div>
            <div class="kpi-card">
              <span class="kpi-label">Active Vehicles</span>
              <span class="kpi-value val-teal">{{ s.activeVehicles }}</span>
              <span class="kpi-sub">Ready for tourist booking</span>
            </div>
            <div class="kpi-card">
              <span class="kpi-label">Pending Review</span>
              <span class="kpi-value val-navy">{{ s.pendingReviewVehicles }}</span>
              <span class="kpi-sub">Awaiting admin moderation</span>
            </div>
            <div class="kpi-card">
              <span class="kpi-label">Expiring Docs (30d)</span>
              <span class="kpi-value" [class.val-coral]="s.expiringDocumentsCount > 0">{{ s.expiringDocumentsCount }}</span>
              <span class="kpi-sub">RC / Insurance / Permits</span>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .dashboard-page { max-width: 1200px; margin: 0 auto; }
    .header-card {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      box-shadow: var(--ir-shadow-sm);
    }
    .page-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .page-head h2 {
      font-size: 1.625rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.25rem 0;
    }
    .actions-row {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-top: 1.25rem;
    }
    .kpi-card {
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-sm);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .kpi-label {
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ir-muted);
    }
    .kpi-value {
      font-size: 1.625rem;
      font-weight: 800;
      color: var(--ir-navy);
    }
    .val-teal { color: var(--ir-teal); }
    .val-navy { color: var(--ir-navy); }
    .val-coral { color: var(--ir-coral); }
    .status-approved { color: #15803d; }
    .kpi-sub {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }
  `],
})
export class PartnerDashboardComponent implements OnInit {
  private readonly partnerService = inject(PartnerInventoryService);
  private readonly authService = inject(AuthService);

  summary = signal<PartnerInventorySummaryDto | null>(null);

  readonly canWrite = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    const hasRole = roles.includes('ROLE_PARTNER_OWNER') || roles.includes('ROLE_PARTNER_MANAGER');
    return hasRole && this.summary()?.businessVerificationStatus === 'APPROVED';
  });

  ngOnInit(): void {
    this.partnerService.getSummary().subscribe({ next: (s) => this.summary.set(s) });
  }
}
