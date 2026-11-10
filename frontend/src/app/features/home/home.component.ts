import { Component } from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  template: `
    <div class="hero-card">
      <h1>Discover & Rent Vehicles in the Andaman Islands</h1>
      <p>Bikes, Scooters, and Self-Drive Cars across Port Blair, Havelock, and Neil Island.</p>
    </div>
  `,
  styles: [`
    .hero-card {
      background: linear-gradient(135deg, #1457d9, #0284c7);
      color: #fff;
      padding: 3rem 2rem;
      border-radius: var(--radius-lg);
      text-align: center;
    }
    h1 { font-size: 2rem; margin-bottom: 0.75rem; }
    p { font-size: 1.125rem; opacity: 0.9; }
  `]
})
export class HomeComponent {}
