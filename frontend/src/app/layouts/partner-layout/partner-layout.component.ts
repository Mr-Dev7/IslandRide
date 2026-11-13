import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-partner-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="partner-layout-container">
      <aside class="partner-sidebar" [class.open]="sidebarOpen()">
        <div class="partner-brand">
          <span class="brand-island">Island</span><span class="brand-ride">Ride</span>
          <span class="brand-tag">Partner</span>
        </div>
        <nav class="partner-nav">
          <a routerLink="/partner" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" (click)="closeSidebar()">Dashboard</a>
          <a routerLink="/partner/fleet" routerLinkActive="active" (click)="closeSidebar()">Fleet Inventory</a>
          <a routerLink="/partner/bookings" routerLinkActive="active" (click)="closeSidebar()">Bookings & Handovers</a>
          <a routerLink="/partner/calendar" routerLinkActive="active" (click)="closeSidebar()">Calendar & Hubs</a>
          <a routerLink="/partner/financials" routerLinkActive="active" (click)="closeSidebar()">Financials & Payouts</a>
        </nav>
      </aside>

      @if (sidebarOpen()) {
        <div class="sidebar-backdrop" (click)="closeSidebar()"></div>
      }

      <div class="partner-main">
        <header class="partner-topbar">
          <div class="topbar-left">
            <button type="button" class="btn-sidebar-toggle" (click)="toggleSidebar()" aria-label="Toggle navigation menu">
              ☰
            </button>
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
    .partner-layout-container { display: flex; min-height: 100vh; background: var(--ir-bg-canvas); }
    .partner-sidebar {
      width: 250px;
      background: var(--ir-navy);
      color: #f8fafc;
      padding: 1.5rem 1rem;
      display: flex;
      flex-direction: column;
      flex-shrink: 0;
      transition: transform 0.25s ease;
      z-index: 100;
    }
    .partner-brand {
      display: flex;
      align-items: baseline;
      gap: 0.2rem;
      font-size: 1.35rem;
      font-weight: 700;
      margin-bottom: 2rem;
      padding-bottom: 0.75rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }
    .brand-island { color: #ffffff; }
    .brand-ride { color: var(--ir-teal); }
    .brand-tag {
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      background: rgba(22, 184, 166, 0.2);
      color: var(--ir-teal);
      padding: 2px 6px;
      border-radius: 4px;
      margin-left: 0.4rem;
    }
    .partner-nav { display: flex; flex-direction: column; gap: 0.4rem; }
    .partner-nav a {
      color: #cbd5e1;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.875rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      transition: all 0.15s ease;
    }
    .partner-nav a:hover {
      background: rgba(255, 255, 255, 0.08);
      color: #ffffff;
      text-decoration: none;
    }
    .partner-nav a.active {
      background: rgba(22, 184, 166, 0.15);
      color: #ffffff;
      border-left: 3px solid var(--ir-teal);
      font-weight: 600;
    }
    .partner-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .partner-topbar {
      background: #ffffff;
      padding: 0.85rem 1.75rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--ir-border);
      box-shadow: var(--shadow-sm);
    }
    .topbar-left { display: flex; align-items: center; gap: 0.75rem; }
    .btn-sidebar-toggle {
      display: none;
      background: none;
      border: 1px solid var(--ir-border);
      border-radius: 6px;
      font-size: 1.25rem;
      padding: 4px 10px;
      cursor: pointer;
      color: var(--ir-navy);
    }
    .hub-label {
      font-size: 0.8125rem;
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      font-weight: 600;
      padding: 3px 10px;
      border-radius: 999px;
      border: 1px solid var(--ir-sea-mist-border);
    }
    .topbar-right { display: flex; align-items: center; gap: 0.85rem; }
    .user-pill {
      font-size: 0.8125rem;
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      padding: 4px 10px;
      border-radius: 999px;
      font-weight: 500;
    }
    .btn-logout {
      background: none;
      border: 1px solid var(--ir-border);
      padding: 5px 12px;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      color: var(--ir-muted);
      transition: all 0.15s ease;
    }
    .btn-logout:hover {
      background: #fee2e2;
      border-color: #fca5a5;
      color: #991b1b;
    }
    .partner-content { padding: 1.75rem; flex: 1; max-width: 1400px; width: 100%; margin: 0 auto; }

    @media (max-width: 860px) {
      .btn-sidebar-toggle { display: block; }
      .partner-sidebar {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        transform: translateX(-100%);
      }
      .partner-sidebar.open {
        transform: translateX(0);
      }
      .sidebar-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(18, 59, 74, 0.6);
        z-index: 90;
      }
      .partner-content { padding: 1rem; }
    }
  `]
})
export class PartnerLayoutComponent {
  readonly authService = inject(AuthService);
  sidebarOpen = signal(false);

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
