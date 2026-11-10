import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-tourist-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <div class="tourist-layout-container">
      <header class="tourist-header">
        <a routerLink="/" class="logo">IslandRide <span>Tourist</span></a>
        <nav class="nav-links">
          <a routerLink="/tourist/bookings">My Bookings</a>
          <a routerLink="/tourist/profile">Profile</a>
        </nav>
      </header>
      <main class="tourist-content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .tourist-layout-container { display: flex; flex-direction: column; min-height: 100vh; }
    .tourist-header { background: #ffffff; border-bottom: 1px solid var(--color-border); padding: 1rem 2rem; display: flex; justify-content: space-between; }
    .tourist-content { flex: 1; max-width: 1100px; margin: 2rem auto; width: 100%; padding: 0 1rem; }
  `]
})
export class TouristLayoutComponent {}

