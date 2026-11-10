import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-partner-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  template: `
    <div class="partner-layout-container">
      <aside class="partner-sidebar">
        <div class="sidebar-brand">IslandRide <span>Partner</span></div>
        <nav class="sidebar-nav">
          <a routerLink="/partner">Dashboard</a>
          <a routerLink="/partner/fleet">Fleet Inventory</a>
          <a routerLink="/partner/calendar">Calendar</a>
          <a routerLink="/partner/financials">Financials</a>
        </nav>
      </aside>
      <div class="partner-main">
        <header class="partner-topbar">
          <div class="topbar-left">
            <span class="hub-label">Andaman Fleet Console</span>
          </div>
          <div class="topbar-right">
            <span class="user-pill">{{ authService.currentUser()?.email }}</span>
            <button (click)="logout()" class="btn-logout">Sign Out</button>
          </div>
        </header>
        <main class="partner-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styles: [`
    .partner-layout-container { display: flex; min-height: 100vh; }
    .partner-sidebar { width: 240px; background: #1e293b; color: #f8fafc; padding: 1.5rem 1rem; }
    .sidebar-brand { font-size: 1.25rem; font-weight: 700; margin-bottom: 2rem; }
    .sidebar-brand span { color: #38bdf8; }
    .sidebar-nav { display: flex; flex-direction: column; gap: 0.5rem; }
    .sidebar-nav a { color: #cbd5e1; padding: 0.5rem 0.75rem; border-radius: 6px; }
    .sidebar-nav a:hover { background: #334155; color: #fff; text-decoration: none; }
    .partner-main { flex: 1; display: flex; flex-direction: column; background: #f1f5f9; }
    .partner-topbar { background: #fff; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--color-border); }
    .topbar-right { display: flex; align-items: center; gap: 1rem; }
    .user-pill { background: #f1f5f9; color: #334155; font-size: 0.8125rem; padding: 4px 10px; border-radius: 999px; }
    .btn-logout { background: none; border: 1px solid #cbd5e1; padding: 4px 10px; border-radius: 4px; font-size: 0.8125rem; cursor: pointer; color: #475569; }
    .btn-logout:hover { background: #f1f5f9; }
    .partner-content { padding: 2rem; flex: 1; }
  `]
})
export class PartnerLayoutComponent {
  readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout().subscribe();
  }
}
