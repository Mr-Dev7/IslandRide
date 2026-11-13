import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="admin-dashboard-page">
      <div class="ir-card pad header-card">
        <div class="page-head">
          <div>
            <h2>Marketplace Governance & Administration</h2>
            <p class="muted">Verify partner licenses, moderate vehicle listings, configure catalog taxonomy & govern marketplace operations.</p>
          </div>
          @if (canModerateFleet()) {
            <div class="actions-row">
              <a routerLink="/admin/fleet" class="ir-btn ir-btn-primary">Fleet Moderation Queue</a>
              <a routerLink="/admin/catalog-config" class="ir-btn ir-btn-outline">Catalog & Island Config</a>
            </div>
          }
        </div>

        <div class="governance-grid">
          <div class="gov-card">
            <h3>Fleet Moderation</h3>
            <p>Review vehicle listings, registration certificates, insurance, and fitness papers before public listing.</p>
            <a routerLink="/admin/fleet" class="link-btn">View Moderation Queue &rarr;</a>
          </div>

          <div class="gov-card">
            <h3>Bookings & Operations</h3>
            <p>Monitor island-wide trip schedules, manage disruption overrides for weather/ferries, and resolve disputes.</p>
            <a routerLink="/admin/bookings" class="link-btn">Open Operations Console &rarr;</a>
          </div>

          <div class="gov-card">
            <h3>Finance & Fiduciary Trust</h3>
            <p>Audit ring-fenced security deposits, monitor partner commission, and manage refund orchestrations.</p>
            <a routerLink="/admin/finance" class="link-btn">Open Finance Ledger &rarr;</a>
          </div>

          <div class="gov-card">
            <h3>Catalog & Island Config</h3>
            <p>Configure Andaman island hubs, vehicle categories, standard deposit baselines, and commission rates.</p>
            <a routerLink="/admin/catalog-config" class="link-btn">Manage Catalog Settings &rarr;</a>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .admin-dashboard-page { max-width: 1200px; margin: 0 auto; }
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
      margin-bottom: 2rem;
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
    .governance-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 1.25rem;
    }
    .gov-card {
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-sm);
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .gov-card h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0;
    }
    .gov-card p {
      font-size: 0.875rem;
      color: var(--ir-charcoal);
      margin: 0;
      line-height: 1.45;
      flex: 1;
    }
    .link-btn {
      color: var(--ir-teal);
      font-size: 0.875rem;
      font-weight: 700;
      text-decoration: none;
      margin-top: 0.5rem;
      display: inline-block;
      transition: color var(--ir-transition);
    }
    .link-btn:hover {
      color: #0f8275;
      text-decoration: underline;
    }
  `],
})
export class AdminDashboardComponent {
  private readonly authService = inject(AuthService);

  readonly canModerateFleet = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_OPERATIONS_ADMIN');
  });
}
