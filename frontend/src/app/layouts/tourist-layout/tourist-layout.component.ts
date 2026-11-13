import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-tourist-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  template: `
    <div class="tourist-layout-container">
      <header class="tourist-header">
        <div class="header-inner">
          <a routerLink="/" class="logo">
            <span class="logo-island">Island</span><span class="logo-ride">Ride</span>
            <span class="badge-portal">Tourist Portal</span>
          </a>
          <nav class="nav-links">
            <a routerLink="/search" class="nav-link">Find Vehicles</a>
            <a routerLink="/tourist/bookings" class="nav-link active">My Trips</a>
            <div class="user-pill">
              <span class="user-name">{{ authService.currentUser()?.firstName || authService.currentUser()?.email }}</span>
              <button (click)="logout()" class="btn-logout" title="Sign Out">Sign Out</button>
            </div>
          </nav>
        </div>
      </header>
      <main class="tourist-content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .tourist-layout-container {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
      background: var(--ir-bg-canvas);
    }
    .tourist-header {
      background: var(--ir-surface);
      border-bottom: 1px solid var(--ir-border);
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: var(--ir-shadow-sm);
    }
    .header-inner {
      max-width: 1100px;
      margin: 0 auto;
      padding: 0.875rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .logo {
      font-size: 1.375rem;
      font-weight: 800;
      text-decoration: none;
      display: flex;
      align-items: center;
      letter-spacing: -0.02em;
    }
    .logo-island { color: var(--ir-navy); }
    .logo-ride { color: var(--ir-teal); }
    .badge-portal {
      font-size: 0.7rem;
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      padding: 3px 8px;
      border-radius: var(--ir-radius-full);
      margin-left: 0.5rem;
      font-weight: 600;
      border: 1px solid #c2e0d8;
    }
    .nav-links {
      display: flex;
      gap: 1.25rem;
      align-items: center;
      flex-wrap: wrap;
    }
    .nav-link {
      color: var(--ir-charcoal);
      text-decoration: none;
      font-weight: 600;
      font-size: 0.875rem;
      transition: color var(--ir-transition);
    }
    .nav-link:hover, .nav-link.active {
      color: var(--ir-teal);
    }
    .user-pill {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: var(--ir-sea-mist);
      padding: 0.25rem 0.625rem;
      border-radius: var(--ir-radius-full);
      border: 1px solid #c2e0d8;
    }
    .user-name {
      font-size: 0.8125rem;
      color: var(--ir-navy);
      font-weight: 600;
    }
    .btn-logout {
      background: #ffffff;
      border: 1px solid var(--ir-border);
      padding: 0.25rem 0.5rem;
      border-radius: var(--ir-radius-full);
      font-size: 0.75rem;
      cursor: pointer;
      color: var(--ir-charcoal);
      transition: all var(--ir-transition);
      min-height: 28px;
    }
    .btn-logout:hover {
      background: #fee2e2;
      color: #b91c1c;
      border-color: #fca5a5;
    }
    .tourist-content {
      flex: 1;
      max-width: 1100px;
      margin: 1.5rem auto;
      width: 100%;
      padding: 0 1rem;
    }
    @media (max-width: 600px) {
      .header-inner {
        padding: 0.75rem 1rem;
      }
      .nav-links {
        gap: 0.75rem;
      }
    }
  `]
})
export class TouristLayoutComponent {
  readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout().subscribe();
  }
}
