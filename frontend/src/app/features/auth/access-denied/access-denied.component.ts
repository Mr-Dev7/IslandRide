import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-access-denied',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="access-denied-page">
      <div class="access-card-wrap">
        <div class="ir-card pad access-card">
          <div class="icon-wrap">
            <svg class="icon-shield-alert" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>

          <h2>Access Restricted</h2>
          <p class="muted">
            You do not possess the required permissions or role to access this portal or resource.
          </p>

          <div class="account-badge">
            <span class="muted small">Signed in as:</span>
            <strong class="account-email">{{ authService.currentUser()?.email || 'Anonymous' }}</strong>
          </div>

          <div class="action-buttons">
            <button (click)="goHome()" class="ir-btn ir-btn-outline">
              Return Home
            </button>
            <button (click)="logout()" class="ir-btn ir-btn-danger">
              Sign Out
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .access-denied-page {
      min-height: calc(100vh - 180px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem 1rem;
    }
    .access-card-wrap {
      width: 100%;
      max-width: 480px;
    }
    .access-card {
      background: var(--ir-surface);
      border-radius: var(--ir-radius-lg);
      border: 1px solid var(--ir-border);
      box-shadow: var(--ir-shadow-md);
      text-align: center;
      padding: 2.5rem 2rem;
    }
    .icon-wrap {
      width: 64px;
      height: 64px;
      margin: 0 auto 1.25rem;
      border-radius: 50%;
      background: #fee2e2;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .icon-shield-alert {
      width: 32px;
      height: 32px;
      color: #dc2626;
    }
    .access-card h2 {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin-bottom: 0.5rem;
    }
    .muted {
      color: var(--ir-muted);
      font-size: 0.9375rem;
      line-height: 1.5;
      margin-bottom: 1.5rem;
    }
    .account-badge {
      display: inline-flex;
      flex-direction: column;
      gap: 0.25rem;
      padding: 0.75rem 1.25rem;
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-md);
      margin-bottom: 1.75rem;
    }
    .account-email {
      color: var(--ir-navy);
      font-size: 0.875rem;
      word-break: break-all;
    }
    .small {
      font-size: 0.75rem;
    }
    .action-buttons {
      display: flex;
      gap: 0.75rem;
      justify-content: center;
    }
    @media (max-width: 480px) {
      .action-buttons {
        flex-direction: column;
      }
    }
  `]
})
export class AccessDeniedComponent {
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  goHome(): void {
    this.router.navigate(['/']);
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/auth/login']);
    });
  }
}
