import { Component } from '@angular/core';

@Component({
  selector: 'app-partner-dashboard',
  standalone: true,
  template: `
    <div class="card">
      <h2>Partner Operations Console</h2>
      <p>Manage your fleet inventory, vehicle availability, approval requests, and pickup checklists.</p>
    </div>
  `,
  styles: [`.card { background: #fff; padding: 2rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); }`]
})
export class PartnerDashboardComponent {}

