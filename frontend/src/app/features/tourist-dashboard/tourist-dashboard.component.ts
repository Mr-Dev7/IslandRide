import { Component } from '@angular/core';

@Component({
  selector: 'app-tourist-dashboard',
  standalone: true,
  template: `
    <div class="card">
      <h2>Tourist Reservations</h2>
      <p>View your confirmed bookings, download trip vouchers, and track refunds.</p>
    </div>
  `,
  styles: [`.card { background: #fff; padding: 2rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); }`]
})
export class TouristDashboardComponent {}

