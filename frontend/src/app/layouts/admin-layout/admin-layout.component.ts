import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  template: `
    <div class="admin-layout-container">
      <aside class="admin-sidebar">
        <div class="admin-brand">IslandRide <span>Admin</span></div>
        <nav class="admin-nav">
          <a routerLink="/admin">Overview</a>
          <a routerLink="/admin/partners">Partner KYC</a>
          @if (canModerateFleet()) {
            <a routerLink="/admin/fleet">Fleet Moderation</a>
            <a routerLink="/admin/catalog-config">Catalog & Islands</a>
          }
          <a routerLink="/admin/bookings">Bookings</a>
          <a routerLink="/admin/finance">Finance & Trust</a>
          <a routerLink="/admin/settlements">Settlements</a>
        </nav>
      </aside>
      <div class="admin-main">
        <header class="admin-topbar">
          <div class="topbar-left">
            <span class="env-pill">Governance Console</span>
          </div>
          <div class="topbar-right">
            <span class="user-pill">{{ authService.currentUser()?.email }}</span>
            <button (click)="logout()" class="btn-logout">Sign Out</button>
          </div>
        </header>
        <main class="admin-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styles: [`
    .admin-layout-container { display: flex; min-height: 100vh; }
    .admin-sidebar { width: 250px; background: #0f172a; color: #f8fafc; padding: 1.5rem 1rem; }
    .admin-brand { font-size: 1.25rem; font-weight: 700; margin-bottom: 2rem; }
    .admin-brand span { color: #f59e0b; }
    .admin-nav { display: flex; flex-direction: column; gap: 0.5rem; }
    .admin-nav a { color: #94a3b8; padding: 0.5rem 0.75rem; border-radius: 6px; }
    .admin-nav a:hover { background: #1e293b; color: #fff; text-decoration: none; }
    .admin-main { flex: 1; display: flex; flex-direction: column; background: #f8fafc; }
    .admin-topbar { background: #fff; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--color-border); }
    .topbar-right { display: flex; align-items: center; gap: 1rem; }
    .env-pill { font-size: 0.8125rem; background: #fef3c7; color: #92400e; padding: 2px 8px; border-radius: 999px; }
    .user-pill { font-size: 0.8125rem; background: #e2e8f0; color: #1e293b; padding: 2px 8px; border-radius: 999px; }
    .btn-logout { background: none; border: 1px solid #cbd5e1; padding: 4px 10px; border-radius: 4px; font-size: 0.8125rem; cursor: pointer; color: #475569; }
    .btn-logout:hover { background: #f1f5f9; }
    .admin-content { padding: 2rem; flex: 1; }
  `]
})
export class AdminLayoutComponent {
  readonly authService = inject(AuthService);

  readonly canModerateFleet = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_OPERATIONS_ADMIN');
  });

  logout(): void {
    this.authService.logout().subscribe();
  }
}
