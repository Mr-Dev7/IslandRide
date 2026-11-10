import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-partner-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
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
          <span class="hub-label">Hub: Havelock Jetty</span>
          <span class="user-pill">Partner Manager</span>
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
    .partner-topbar { background: #fff; padding: 1rem 2rem; display: flex; justify-content: space-between; border-bottom: 1px solid var(--color-border); }
    .partner-content { padding: 2rem; flex: 1; }
  `]
})
export class PartnerLayoutComponent {}

