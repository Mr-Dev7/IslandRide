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
    <div class="ir-card pad mb-md">
      <div class="page-head">
        <div>
          <h2>Partner Operations Console</h2>
          <p class="muted">Manage your Andaman & Nicobar fleet inventory, pricing plans, documents, and availability blocks.</p>
        </div>
        <div class="actions-row">
          <a routerLink="/partner/fleet" class="ir-btn ir-btn-primary ir-btn-sm">Manage Fleet</a>
          @if (canWrite()) {
            <a routerLink="/partner/fleet/new" class="ir-btn ir-btn-outline ir-btn-sm">+ Add Vehicle</a>
          }
          <a routerLink="/partner/calendar" class="ir-btn ir-btn-outline ir-btn-sm">Calendar & Hubs</a>
        </div>
      </div>

      @if (summary(); as s) {
        <div class="kpi-grid mt-sm">
          <div class="kpi-card"><div class="kpi-label">Business Status</div><div class="kpi-value">{{ s.businessVerificationStatus }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Active Vehicles</div><div class="kpi-value">{{ s.activeVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Pending Review</div><div class="kpi-value">{{ s.pendingReviewVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Expiring Docs (30d)</div><div class="kpi-value">{{ s.expiringDocumentsCount }}</div></div>
        </div>
      }
    </div>
  `,
  styles: [``],
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
