import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card-wrap">
        <div class="auth-header">
          <h2>Create Tourist Account</h2>
          <p class="muted">Sign up to explore Andaman & Nicobar on two and four wheels</p>
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

          <form [formGroup]="registerForm" (ngSubmit)="onSubmit()">
            <div class="field-row">
              <div class="field">
                <label for="reg-fname">First Name</label>
                <input
                  id="reg-fname"
                  type="text"
                  formControlName="firstName"
                  required
                  class="ir-input"
                />
              </div>
              <div class="field">
                <label for="reg-lname">Last Name</label>
                <input
                  id="reg-lname"
                  type="text"
                  formControlName="lastName"
                  required
                  class="ir-input"
                />
              </div>
            </div>

            <div class="field">
              <label for="reg-email">Email Address</label>
              <input
                id="reg-email"
                type="email"
                formControlName="email"
                required
                class="ir-input"
              />
            </div>

            <div class="field">
              <label for="reg-phone">Phone (optional)</label>
              <input
                id="reg-phone"
                type="tel"
                formControlName="phone"
                placeholder="+91 98765 43210"
                class="ir-input"
              />
            </div>

            <div class="field">
              <label for="reg-pass">Password</label>
              <input
                id="reg-pass"
                type="password"
                formControlName="password"
                required
                class="ir-input"
              />
              <span class="muted small mt-xs">Minimum 8 characters</span>
            </div>

            <div class="submit-wrap">
              <button
                type="submit"
                [disabled]="registerForm.invalid || isLoading()"
                class="ir-btn ir-btn-primary full-width"
              >
                @if (isLoading()) {
                  <span>Creating account...</span>
                } @else {
                  <span>Create Account</span>
                }
              </button>
            </div>
          </form>

          <div class="auth-footer-links">
            <p class="muted small">
              Already have an account?
              <a routerLink="/auth/login" class="link-btn small">
                Sign in
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
      max-width: 480px;
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
    .submit-wrap {
      margin-top: 1rem;
    }
    .auth-footer-links {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--ir-border);
      text-align: center;
    }
  `]
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly registerForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  onSubmit(): void {
    if (this.registerForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const val = this.registerForm.value;
    this.authService
      .register({
        firstName: val.firstName!,
        lastName: val.lastName!,
        email: val.email!,
        phone: val.phone || undefined,
        password: val.password!,
      })
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.successMessage.set('Account created successfully! Redirecting to login...');
          setTimeout(() => this.router.navigate(['/auth/login']), 1500);
        },
        error: (err) => {
          this.isLoading.set(false);
          this.errorMessage.set(err.error?.detail || 'Failed to create account. Please try again.');
        },
      });
  }
}
