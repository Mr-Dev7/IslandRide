import { Component } from '@angular/core';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  template: `
    <div class="card">
      <h2>Marketplace Governance & Administration</h2>
      <p>Verify partner licenses, moderate vehicle listings, arbitrate disputes, and disburse settlements.</p>
    </div>
  `,
  styles: [`.card { background: #fff; padding: 2rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); }`]
})
export class AdminDashboardComponent {}

