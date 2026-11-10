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
        <a routerLink="/" class="logo">IslandRide <span>Tourist</span></a>
        <nav class="nav-links">
          <a routerLink="/tourist/bookings">My Bookings</a>
          <span class="user-badge">{{ authService.currentUser()?.firstName || authService.currentUser()?.email }}</span>
          <button (click)="logout()" class="btn-logout">Sign Out</button>
        </nav>
      </header>
      <main class="tourist-content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .tourist-layout-container { display: flex; flex-direction: column; min-height: 100vh; }
    .tourist-header { background: #ffffff; border-bottom: 1px solid var(--color-border); padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; }
    .nav-links { display: flex; gap: 1.25rem; align-items: center; }
    .user-badge { font-size: 0.875rem; color: #475569; font-weight: 500; }
    .btn-logout { background: none; border: 1px solid #cbd5e1; padding: 4px 10px; border-radius: 4px; font-size: 0.8125rem; cursor: pointer; color: #475569; }
    .btn-logout:hover { background: #f1f5f9; }
    .tourist-content { flex: 1; max-width: 1100px; margin: 2rem auto; width: 100%; padding: 0 1rem; }
  `]
})
export class TouristLayoutComponent {
  readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout().subscribe();
  }
}
