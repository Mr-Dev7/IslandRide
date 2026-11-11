import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="ir-card pad">
      <div class="page-head">
        <div>
          <h2>Marketplace Governance & Administration</h2>
          <p class="muted">Verify partner licenses, moderate vehicle listings, configure catalog taxonomy & govern marketplace operations.</p>
        </div>
        @if (canModerateFleet()) {
          <div class="actions-row">
            <a routerLink="/admin/fleet" class="ir-btn ir-btn-primary ir-btn-sm">Fleet Moderation Queue</a>
            <a routerLink="/admin/catalog-config" class="ir-btn ir-btn-outline ir-btn-sm">Catalog & Island Config</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [``],
})
export class AdminDashboardComponent {
  private readonly authService = inject(AuthService);

  readonly canModerateFleet = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_OPERATIONS_ADMIN');
  });
}
