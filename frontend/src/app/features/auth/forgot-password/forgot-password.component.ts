import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card-wrap">
        <div class="auth-header">
          <h2>Reset Your Password</h2>
          <p class="muted">Enter your registered email address to receive password reset instructions</p>
        </div>

        <div class="ir-card pad auth-card">
          @if (submitted()) {
            <div class="ir-alert ir-alert-success">
              If an account exists with this email, a secure password reset link has been dispatched. Please check your inbox.
            </div>
            <div class="reset-link-row">
              <a routerLink="/auth/reset-password" class="link-btn small">
                Already have a reset token? Enter it here &rarr;
              </a>
            </div>
          } @else {
            <form [formGroup]="forgotForm" (ngSubmit)="onSubmit()">
              <div class="field">
                <label for="forgot-email">Email Address</label>
                <input
                  id="forgot-email"
                  type="email"
                  formControlName="email"
                  required
                  placeholder="name@example.com"
                  class="ir-input"
                />
              </div>

              <div class="submit-wrap">
                <button
                  type="submit"
                  [disabled]="forgotForm.invalid || isLoading()"
                  class="ir-btn ir-btn-primary full-width"
                >
                  @if (isLoading()) {
                    <span>Dispatching link...</span>
                  } @else {
                    <span>Send Reset Link</span>
                  }
                </button>
              </div>
            </form>
          }

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
      font-size: 1.65rem;
      margin-bottom: 0.35rem;
    }
    .auth-card {
      padding: 2rem;
      background: #ffffff;
    }
    .reset-link-row {
      margin-top: 1rem;
      text-align: center;
    }
    .submit-wrap {
      margin-top: 1.25rem;
    }
    .auth-footer-links {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--ir-border);
      text-align: center;
    }
  `]
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly isLoading = signal(false);
  readonly submitted = signal(false);

  readonly forgotForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  onSubmit(): void {
    if (this.forgotForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    const email = this.forgotForm.value.email!;

    this.authService.forgotPassword({ email }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.isLoading.set(false);
        this.submitted.set(true);
      },
    });
  }
}
