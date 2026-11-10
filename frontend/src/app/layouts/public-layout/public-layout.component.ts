import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
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
            <a routerLink="/auth/login" class="btn-login">Sign In</a>
            <a routerLink="/partner" class="btn-partner">Partner Portal</a>
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
    .btn-partner { background: var(--color-primary); color: #fff; padding: 6px 14px; border-radius: var(--radius-sm); }
    .public-content { flex: 1; max-width: 1200px; width: 100%; margin: 2rem auto; padding: 0 1rem; }
    .public-footer { background: #0f172a; color: #94a3b8; text-align: center; padding: 2rem 1rem; font-size: 0.875rem; }
  `]
})
export class PublicLayoutComponent {}

