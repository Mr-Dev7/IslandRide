import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card-wrap">
        <div class="auth-header">
          <h2>IslandRide Portal Login</h2>
          <p class="muted">Access your Tourist, Partner, or Governance console</p>
        </div>

        <div class="ir-card pad auth-card">
          @if (errorMessage()) {
            <div class="ir-alert ir-alert-error">
              {{ errorMessage() }}
            </div>
          }

          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
            <div class="field">
              <label for="email">Email address</label>
              <input
                id="email"
                type="email"
                formControlName="email"
                autocomplete="email"
                required
                class="ir-input"
                placeholder="name@example.com"
              />
            </div>

            <div class="field">
              <label for="password">Password</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                autocomplete="current-password"
                required
                class="ir-input"
              />
            </div>

            <div class="forgot-row">
              <a routerLink="/auth/forgot-password" class="link-btn small">
                Forgot your password?
              </a>
            </div>

            <div class="submit-wrap">
              <button
                type="submit"
                [disabled]="loginForm.invalid || isLoading()"
                class="ir-btn ir-btn-primary full-width"
              >
                @if (isLoading()) {
                  <span>Signing in...</span>
                } @else {
                  <span>Sign in</span>
                }
              </button>
            </div>
          </form>

          <div class="auth-footer-links">
            <p class="muted small">
              New tourist?
              <a routerLink="/auth/register" class="link-btn small">
                Create an account
              </a>
            </p>
            <p class="muted small mt-xs">
              Vehicle rental agency?
              <a routerLink="/partner/register" class="link-btn small">
                Partner Onboarding
              </a>
            </p>
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
    .forgot-row {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 1.25rem;
    }
    .submit-wrap {
      margin-top: 0.5rem;
    }
    .auth-footer-links {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--ir-border);
      text-align: center;
    }
  `]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.loginForm.value;
    this.authService.login({ email: email!, password: password! }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        const returnUrl = this.route.snapshot.queryParams['returnUrl'];
        if (returnUrl) {
          this.router.navigateByUrl(returnUrl);
          return;
        }

        const roles = res.user.roles || [];
        if (roles.some((r) => r.includes('ADMIN') || r === 'ROLE_SUPPORT_AGENT')) {
          this.router.navigate(['/admin']);
        } else if (roles.some((r) => r.startsWith('ROLE_PARTNER'))) {
          this.router.navigate(['/partner']);
        } else {
          this.router.navigate(['/tourist']);
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 401) {
          this.errorMessage.set('Invalid email or password. Please try again.');
        } else if (err.status === 423) {
          this.errorMessage.set('Your account has been locked. Please contact support.');
        } else {
          this.errorMessage.set(err.error?.detail || 'An unexpected authentication error occurred.');
        }
      },
    });
  }
}
