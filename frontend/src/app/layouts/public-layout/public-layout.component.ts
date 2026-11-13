import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  template: `
    <div class="public-layout-container">
      <header class="public-header">
        <div class="header-inner">
          <a routerLink="/" class="logo" (click)="closeMobileMenu()">
            <span class="logo-island">Island</span><span class="logo-ride">Ride</span>
            <span class="badge-geo">Andaman & Nicobar</span>
          </a>

          <!-- Desktop Navigation -->
          <nav class="nav-links desktop-nav">
            <a routerLink="/search" class="nav-link">Browse Vehicles</a>
            @if (authService.isAuthenticated()) {
              @if (authService.isAdmin()) {
                <a routerLink="/admin" class="btn-portal">Admin Console</a>
              } @else if (authService.isPartner()) {
                <a routerLink="/partner" class="btn-portal">Partner Portal</a>
              } @else {
                <a routerLink="/tourist" class="btn-portal">My Bookings</a>
              }
              <span class="user-greeting">Hi, {{ authService.currentUser()?.firstName }}</span>
              <button (click)="logout()" class="btn-logout">Sign Out</button>
            } @else {
              <a routerLink="/auth/login" class="nav-link btn-login">Sign In</a>
              <a routerLink="/partner/register" class="btn-partner">Partner Onboarding</a>
            }
          </nav>

          <!-- Mobile Hamburger Button -->
          <button class="mobile-hamburger" (click)="toggleMobileMenu()" aria-label="Toggle Navigation">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              @if (!mobileMenuOpen()) {
                <line x1="3" y1="12" x2="21" y2="12"></line>
                <line x1="3" y1="6" x2="21" y2="6"></line>
                <line x1="3" y1="18" x2="21" y2="18"></line>
              } @else {
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              }
            </svg>
          </button>
        </div>

        <!-- Mobile Drawer -->
        @if (mobileMenuOpen()) {
          <div class="mobile-drawer">
            <a routerLink="/search" class="mobile-nav-link" (click)="closeMobileMenu()">Browse Vehicles</a>
            @if (authService.isAuthenticated()) {
              @if (authService.isAdmin()) {
                <a routerLink="/admin" class="mobile-nav-link portal-link" (click)="closeMobileMenu()">Admin Console</a>
              } @else if (authService.isPartner()) {
                <a routerLink="/partner" class="mobile-nav-link portal-link" (click)="closeMobileMenu()">Partner Portal</a>
              } @else {
                <a routerLink="/tourist" class="mobile-nav-link portal-link" (click)="closeMobileMenu()">My Bookings</a>
              }
              <div class="mobile-user-row">
                <span class="user-greeting">Signed in as {{ authService.currentUser()?.firstName }}</span>
                <button (click)="logout(); closeMobileMenu()" class="btn-logout">Sign Out</button>
              </div>
            } @else {
              <a routerLink="/auth/login" class="mobile-nav-link" (click)="closeMobileMenu()">Sign In</a>
              <a routerLink="/partner/register" class="btn-partner mobile-cta" (click)="closeMobileMenu()">Partner Onboarding</a>
            }
          </div>
        }
      </header>

      <main class="public-content">
        <router-outlet></router-outlet>
      </main>

      <footer class="public-footer">
        <div class="footer-inner">
          <p class="footer-brand">
            <strong>IslandRide</strong> &bull; Andaman & Nicobar Vehicle Rental Marketplace
          </p>
          <p class="footer-islands">
            Sri Vijaya Puram (Port Blair) &bull; Swaraj Dweep (Havelock) &bull; Shaheed Dweep (Neil)
          </p>
          <p class="footer-copy">
            &copy; 2026 IslandRide. Built for Andaman travel ergonomics.
          </p>
        </div>
      </footer>
    </div>
  `,
  styles: [`
    .public-layout-container {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
      background: var(--ir-bg-canvas);
    }
    .public-header {
      background: var(--ir-surface);
      border-bottom: 1px solid var(--ir-border);
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: var(--ir-shadow-sm);
    }
    .header-inner {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0.875rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
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
    .badge-geo {
      font-size: 0.7rem;
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      padding: 3px 8px;
      border-radius: var(--ir-radius-full);
      margin-left: 0.5rem;
      font-weight: 600;
      border: 1px solid #c2e0d8;
    }
    .desktop-nav {
      display: flex;
      gap: 1.25rem;
      align-items: center;
    }
    .nav-link {
      color: var(--ir-charcoal);
      text-decoration: none;
      font-weight: 500;
      font-size: 0.9375rem;
      transition: color var(--ir-transition);
    }
    .nav-link:hover {
      color: var(--ir-teal);
    }
    .btn-portal {
      font-weight: 600;
      color: var(--ir-navy);
      text-decoration: none;
      background: var(--ir-sea-mist);
      padding: 0.375rem 0.875rem;
      border-radius: var(--ir-radius-sm);
      font-size: 0.875rem;
      border: 1px solid #c2e0d8;
      transition: all var(--ir-transition);
    }
    .btn-portal:hover {
      background: #d4eade;
    }
    .user-greeting {
      font-size: 0.875rem;
      color: var(--ir-muted);
      font-weight: 500;
    }
    .btn-logout {
      background: none;
      border: 1px solid var(--ir-border);
      padding: 0.35rem 0.75rem;
      border-radius: var(--ir-radius-sm);
      font-size: 0.8125rem;
      cursor: pointer;
      color: var(--ir-charcoal);
      transition: all var(--ir-transition);
      min-height: 36px;
    }
    .btn-logout:hover {
      background: #f1f5f9;
      border-color: #cbd5e1;
    }
    .btn-partner {
      background: var(--ir-teal);
      color: #ffffff;
      padding: 0.5rem 1rem;
      border-radius: var(--ir-radius-sm);
      text-decoration: none;
      font-size: 0.875rem;
      font-weight: 600;
      transition: background-color var(--ir-transition);
      min-height: 40px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .btn-partner:hover {
      background: #0f8275;
    }

    .mobile-hamburger {
      display: none;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0.5rem;
      color: var(--ir-navy);
      width: 44px;
      height: 44px;
      align-items: center;
      justify-content: center;
    }
    .mobile-hamburger svg {
      width: 24px;
      height: 24px;
    }

    .mobile-drawer {
      display: flex;
      flex-direction: column;
      padding: 1rem 1.5rem 1.5rem;
      background: var(--ir-surface);
      border-top: 1px solid var(--ir-border);
      gap: 0.75rem;
    }
    .mobile-nav-link {
      padding: 0.75rem 0.5rem;
      color: var(--ir-charcoal);
      text-decoration: none;
      font-weight: 600;
      font-size: 1rem;
      border-bottom: 1px solid var(--ir-border);
      min-height: 44px;
      display: flex;
      align-items: center;
    }
    .portal-link {
      color: var(--ir-teal);
    }
    .mobile-user-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 0.5rem;
    }
    .mobile-cta {
      margin-top: 0.5rem;
      width: 100%;
      text-align: center;
    }

    @media (max-width: 768px) {
      .desktop-nav { display: none; }
      .mobile-hamburger { display: flex; }
      .header-inner { padding: 0.75rem 1rem; }
    }

    .public-content {
      flex: 1;
      max-width: 1200px;
      width: 100%;
      margin: 1.5rem auto;
      padding: 0 1rem;
    }

    .public-footer {
      background: var(--ir-navy);
      color: #94a3b8;
      text-align: center;
      padding: 2.5rem 1.5rem;
      font-size: 0.875rem;
      margin-top: auto;
    }
    .footer-inner {
      max-width: 800px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .footer-brand {
      color: #ffffff;
      font-size: 0.9375rem;
      margin: 0;
    }
    .footer-islands {
      color: var(--ir-sea-mist);
      font-size: 0.8125rem;
      margin: 0;
    }
    .footer-copy {
      color: #94a3b8;
      font-size: 0.75rem;
      margin-top: 0.5rem;
    }
  `]
})
export class PublicLayoutComponent {
  readonly authService = inject(AuthService);
  mobileMenuOpen = signal(false);

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update(v => !v);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
