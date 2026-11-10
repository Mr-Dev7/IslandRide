import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <div class="admin-layout-container">
      <aside class="admin-sidebar">
        <div class="admin-brand">IslandRide <span>Admin</span></div>
        <nav class="admin-nav">
          <a routerLink="/admin">Overview</a>
          <a routerLink="/admin/partners">Partner KYC</a>
          <a routerLink="/admin/fleet">Fleet Moderation</a>
          <a routerLink="/admin/bookings">Bookings</a>
          <a routerLink="/admin/settlements">Settlements</a>
        </nav>
      </aside>
      <div class="admin-main">
        <header class="admin-topbar">
          <span class="env-pill">Production Operations</span>
          <span class="user-pill">Super Admin</span>
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
    .admin-topbar { background: #fff; padding: 1rem 2rem; display: flex; justify-content: space-between; border-bottom: 1px solid var(--color-border); }
    .env-pill { font-size: 0.8125rem; background: #fef3c7; color: #92400e; padding: 2px 8px; border-radius: 999px; }
    .admin-content { padding: 2rem; flex: 1; }
  `]
})
export class AdminLayoutComponent {}

