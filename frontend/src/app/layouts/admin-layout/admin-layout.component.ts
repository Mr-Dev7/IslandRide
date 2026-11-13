import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="admin-layout-container">
      <aside class="admin-sidebar" [class.open]="sidebarOpen()">
        <div class="admin-brand">
          <span class="brand-island">Island</span><span class="brand-ride">Ride</span>
          <span class="brand-tag">Governance</span>
        </div>
        <nav class="admin-nav">
          <a routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" (click)="closeSidebar()">Overview</a>
          @if (canModerateFleet()) {
            <a routerLink="/admin/fleet" routerLinkActive="active" (click)="closeSidebar()">Fleet Moderation</a>
            <a routerLink="/admin/catalog-config" routerLinkActive="active" (click)="closeSidebar()">Catalog & Islands</a>
          }
          <a routerLink="/admin/bookings" routerLinkActive="active" (click)="closeSidebar()">Operations & Bookings</a>
          <a routerLink="/admin/finance" routerLinkActive="active" (click)="closeSidebar()">Finance & Trust Ledger</a>
        </nav>
      </aside>

      @if (sidebarOpen()) {
        <div class="sidebar-backdrop" (click)="closeSidebar()"></div>
      }

      <div class="admin-main">
        <header class="admin-topbar">
          <div class="topbar-left">
            <button type="button" class="btn-sidebar-toggle" (click)="toggleSidebar()" aria-label="Toggle navigation menu">
              ☰
            </button>
            <span class="env-pill">Andaman Operations Console</span>
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
    .admin-layout-container { display: flex; min-height: 100vh; background: var(--ir-bg-canvas); }
    .admin-sidebar {
      width: 260px;
      background: var(--ir-navy);
      color: #f8fafc;
      padding: 1.5rem 1rem;
      display: flex;
      flex-direction: column;
      flex-shrink: 0;
      transition: transform 0.25s ease;
      z-index: 100;
    }
    .admin-brand {
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
    .admin-nav { display: flex; flex-direction: column; gap: 0.4rem; }
    .admin-nav a {
      color: #cbd5e1;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.875rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      transition: all 0.15s ease;
    }
    .admin-nav a:hover {
      background: rgba(255, 255, 255, 0.08);
      color: #ffffff;
      text-decoration: none;
    }
    .admin-nav a.active {
      background: rgba(22, 184, 166, 0.15);
      color: #ffffff;
      border-left: 3px solid var(--ir-teal);
      font-weight: 600;
    }
    .admin-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .admin-topbar {
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
    .env-pill {
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
    .admin-content { padding: 1.75rem; flex: 1; max-width: 1400px; width: 100%; margin: 0 auto; }

    @media (max-width: 860px) {
      .btn-sidebar-toggle { display: block; }
      .admin-sidebar {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        transform: translateX(-100%);
      }
      .admin-sidebar.open {
        transform: translateX(0);
      }
      .sidebar-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(18, 59, 74, 0.6);
        z-index: 90;
      }
      .admin-content { padding: 1rem; }
    }
  `]
})
export class AdminLayoutComponent {
  readonly authService = inject(AuthService);
  sidebarOpen = signal(false);

  readonly canModerateFleet = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_OPERATIONS_ADMIN');
  });

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
