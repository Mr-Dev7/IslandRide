import { Component, inject } from '@angular/core';
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
          <a routerLink="/" class="logo">
            <span class="logo-island">Island</span><span class="logo-ride">Ride</span>
            <span class="badge-geo">Andaman & Nicobar</span>
          </a>
          <nav class="nav-links">
            <a routerLink="/search">Browse Vehicles</a>
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
              <a routerLink="/auth/login" class="btn-login">Sign In</a>
              <a routerLink="/partner/register" class="btn-partner">Partner Onboarding</a>
            }
          </nav>
        </div>
      </header>
      <main class="public-content">
        <router-outlet></router-outlet>
      </main>
      <footer class="public-footer">
        <p>&copy; 2026 IslandRide Marketplace. Port Blair • Havelock • Neil Island.</p>
      </footer>
    </div>
  `,
  styles: [`
    .public-layout-container { display: flex; flex-direction: column; min-height: 100vh; }
    .public-header { background: #ffffff; border-bottom: 1px solid var(--color-border); padding: 1rem 2rem; }
    .header-inner { max-width: 1200px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; }
    .logo { font-size: 1.5rem; font-weight: 700; text-decoration: none; display: flex; align-items: center; gap: 0.25rem; }
    .logo-island { color: var(--color-primary); }
    .logo-ride { color: var(--color-secondary); }
    .badge-geo { font-size: 0.75rem; background: #e0f2fe; color: #0369a1; padding: 2px 8px; border-radius: 999px; margin-left: 0.5rem; }
    .nav-links { display: flex; gap: 1.5rem; align-items: center; }
    .btn-login { color: var(--color-text-primary); font-weight: 500; }
    .btn-portal { font-weight: 600; color: var(--color-primary); }
    .user-greeting { font-size: 0.875rem; color: #64748b; }
    .btn-logout { background: none; border: 1px solid #cbd5e1; padding: 4px 10px; border-radius: 4px; font-size: 0.8125rem; cursor: pointer; color: #475569; }
    .btn-logout:hover { background: #f1f5f9; }
    .btn-partner { background: var(--color-primary); color: #fff; padding: 6px 14px; border-radius: var(--radius-sm); text-decoration: none; }
    .public-content { flex: 1; max-width: 1200px; width: 100%; margin: 2rem auto; padding: 0 1rem; }
    .public-footer { background: #0f172a; color: #94a3b8; text-align: center; padding: 2rem 1rem; font-size: 0.875rem; }
  `]
})
export class PublicLayoutComponent {
  readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout().subscribe();
  }
}
