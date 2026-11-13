import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card-wrap">
        <div class="auth-header">
          <h2>Set New Password</h2>
          <p class="muted">Enter your recovery token and new account password</p>
        </div>

        <div class="ir-card pad auth-card">
          @if (errorMessage()) {
            <div class="ir-alert ir-alert-error">
              {{ errorMessage() }}
            </div>
          }
          @if (successMessage()) {
            <div class="ir-alert ir-alert-success">
              {{ successMessage() }}
            </div>
          }

          <form [formGroup]="resetForm" (ngSubmit)="onSubmit()">
            <div class="field">
              <label for="token">Reset Token</label>
              <input
                id="token"
                type="text"
                formControlName="token"
                required
                placeholder="UUID or token from email"
                class="ir-input"
              />
            </div>

            <div class="field">
              <label for="newPassword">New Password</label>
              <input
                id="newPassword"
                type="password"
                formControlName="newPassword"
                required
                class="ir-input"
              />
              <span class="field-hint">Minimum 8 characters</span>
            </div>

            <div class="submit-wrap">
              <button
                type="submit"
                [disabled]="resetForm.invalid || isLoading()"
                class="ir-btn ir-btn-primary full-width"
              >
                @if (isLoading()) {
                  <span>Updating password...</span>
                } @else {
                  <span>Update Password</span>
                }
              </button>
            </div>
          </form>

          <div class="auth-footer-links">
            <a routerLink="/auth/login" class="link-btn small">
              &larr; Back to login
            </a>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-page {
      min-height: calc(100vh - 180px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem 1rem;
    }
    .auth-card-wrap {
      width: 100%;
      max-width: 440px;
    }
    .auth-header {
      text-align: center;
      margin-bottom: 1.5rem;
    }
    .auth-header h2 {
      margin-bottom: 0.25rem;
      font-size: 1.75rem;
      font-weight: 700;
      color: var(--ir-navy);
      letter-spacing: -0.02em;
    }
    .muted {
      color: var(--ir-muted);
      font-size: 0.875rem;
      margin: 0;
    }
    .auth-card {
      background: var(--ir-surface);
      border-radius: var(--ir-radius-lg);
      border: 1px solid var(--ir-border);
      box-shadow: var(--ir-shadow-md);
    }
    .field {
      margin-bottom: 1.125rem;
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }
    .field label {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--ir-charcoal);
    }
    .field-hint {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }
    .submit-wrap {
      margin-top: 1.5rem;
    }
    .full-width {
      width: 100%;
    }
    .auth-footer-links {
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid var(--ir-border);
      text-align: center;
    }
    .link-btn {
      color: var(--ir-teal);
      text-decoration: none;
      font-weight: 600;
      transition: color var(--ir-transition);
    }
    .link-btn:hover {
      color: #0f8275;
      text-decoration: underline;
    }
    .small {
      font-size: 0.875rem;
    }
  `]
})
export class ResetPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly resetForm = this.fb.group({
    token: [this.route.snapshot.queryParams['token'] || '', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  onSubmit(): void {
    if (this.resetForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { token, newPassword } = this.resetForm.value;
    this.authService.resetPassword({ token: token!, newPassword: newPassword! }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Password updated successfully! Redirecting to login...');
        setTimeout(() => this.router.navigate(['/auth/login']), 2000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.detail || 'Failed to reset password. Token may be invalid or expired.');
      },
    });
  }
}
