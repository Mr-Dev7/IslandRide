import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-partner-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="partner-reg-page">
      <div class="partner-reg-wrap">
        <div class="reg-header">
          <div class="badge-partner">Island Vendor Network</div>
          <h2>Partner Vendor Onboarding</h2>
          <p class="muted">Register your Andaman vehicle rental agency on IslandRide</p>
        </div>

        <div class="ir-card pad reg-card">
          @if (errorMessage()) {
            <div class="ir-alert ir-alert-error">
              {{ errorMessage() }}
            </div>
          }
          @if (successMessage()) {
            <div class="ir-alert ir-alert-success status-box">
              <p class="font-bold">{{ successMessage() }}</p>
              <p>Your business profile has been registered in <strong>DRAFT</strong> status. Our operations team will review your business credentials before inventory publishing is enabled.</p>
            </div>
          }

          <form [formGroup]="partnerForm" (ngSubmit)="onSubmit()">
            <!-- Section 1: Business Profile -->
            <div class="form-section">
              <div class="section-heading">
                <span class="step-num">1</span>
                <h3>Business Profile</h3>
              </div>
              <div class="grid-2">
                <div class="field">
                  <label for="legalName">Legal Business Name *</label>
                  <input
                    id="legalName"
                    type="text"
                    formControlName="legalName"
                    placeholder="e.g. Andaman Island Rentals Pvt Ltd"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="tradeName">Trade / Brand Name *</label>
                  <input
                    id="tradeName"
                    type="text"
                    formControlName="tradeName"
                    placeholder="e.g. Island Wheels Port Blair"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="gstin">GSTIN (optional)</label>
                  <input
                    id="gstin"
                    type="text"
                    formControlName="gstin"
                    placeholder="15-character GSTIN"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="panNumber">PAN Number (optional)</label>
                  <input
                    id="panNumber"
                    type="text"
                    formControlName="panNumber"
                    placeholder="10-character PAN"
                    class="ir-input"
                  />
                </div>
              </div>
            </div>

            <!-- Section 2: Authorized Owner Account -->
            <div class="form-section">
              <div class="section-heading">
                <span class="step-num">2</span>
                <h3>Authorized Owner Account</h3>
              </div>
              <div class="grid-2">
                <div class="field">
                  <label for="firstName">First Name *</label>
                  <input
                    id="firstName"
                    type="text"
                    formControlName="firstName"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="lastName">Last Name *</label>
                  <input
                    id="lastName"
                    type="text"
                    formControlName="lastName"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="email">Owner Email Address *</label>
                  <input
                    id="email"
                    type="email"
                    formControlName="email"
                    class="ir-input"
                  />
                </div>
                <div class="field">
                  <label for="phone">Owner Contact Phone *</label>
                  <input
                    id="phone"
                    type="tel"
                    formControlName="phone"
                    placeholder="+919876543210"
                    class="ir-input"
                  />
                </div>
              </div>

              <div class="field mt-3">
                <label for="password">Password *</label>
                <input
                  id="password"
                  type="password"
                  formControlName="password"
                  class="ir-input"
                />
                <span class="field-hint">Minimum 8 characters</span>
              </div>
            </div>

            <div class="submit-wrap">
              <button
                type="submit"
                [disabled]="partnerForm.invalid || isLoading()"
                class="ir-btn ir-btn-primary full-width"
              >
                @if (isLoading()) {
                  <span>Registering partner business...</span>
                } @else {
                  <span>Submit Partner Registration</span>
                }
              </button>
            </div>
          </form>

          <div class="reg-footer-links">
            <p class="muted small">
              Already registered?
              <a routerLink="/auth/login" class="link-btn">
                Sign in to Partner Console
              </a>
            </p>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .partner-reg-page {
      min-height: calc(100vh - 180px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2.5rem 1rem;
    }
    .partner-reg-wrap {
      width: 100%;
      max-width: 640px;
    }
    .reg-header {
      text-align: center;
      margin-bottom: 1.75rem;
    }
    .badge-partner {
      display: inline-block;
      padding: 0.25rem 0.75rem;
      border-radius: var(--ir-radius-full);
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      margin-bottom: 0.75rem;
      border: 1px solid #c2e0d8;
    }
    .reg-header h2 {
      margin-bottom: 0.35rem;
      font-size: 1.875rem;
      font-weight: 700;
      color: var(--ir-navy);
      letter-spacing: -0.02em;
    }
    .muted {
      color: var(--ir-muted);
      font-size: 0.9375rem;
      margin: 0;
    }
    .reg-card {
      background: var(--ir-surface);
      border-radius: var(--ir-radius-lg);
      border: 1px solid var(--ir-border);
      box-shadow: var(--ir-shadow-md);
      padding: 2rem;
    }
    .status-box {
      margin-bottom: 1.5rem;
    }
    .font-bold {
      font-weight: 700;
      margin-bottom: 0.35rem;
    }
    .form-section {
      margin-bottom: 1.75rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--ir-border);
    }
    .form-section:last-of-type {
      border-bottom: none;
      margin-bottom: 1rem;
      padding-bottom: 0;
    }
    .section-heading {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      margin-bottom: 1.25rem;
    }
    .step-num {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--ir-teal);
      color: #ffffff;
      font-size: 0.75rem;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
    .section-heading h3 {
      font-size: 1rem;
      font-weight: 600;
      color: var(--ir-navy);
      margin: 0;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    @media (max-width: 600px) {
      .grid-2 {
        grid-template-columns: 1fr;
      }
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }
    .field label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--ir-charcoal);
    }
    .field-hint {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }
    .mt-3 {
      margin-top: 1rem;
    }
    .submit-wrap {
      margin-top: 1.5rem;
    }
    .full-width {
      width: 100%;
    }
    .reg-footer-links {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--ir-border);
      text-align: center;
    }
    .link-btn {
      color: var(--ir-teal);
      text-decoration: none;
      font-weight: 600;
      margin-left: 0.25rem;
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
export class PartnerRegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly partnerForm = this.fb.group({
    legalName: ['', [Validators.required]],
    tradeName: ['', [Validators.required]],
    gstin: [''],
    panNumber: [''],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.required]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  onSubmit(): void {
    if (this.partnerForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const val = this.partnerForm.value;
    this.authService
      .registerPartner({
        legalName: val.legalName!,
        tradeName: val.tradeName!,
        gstin: val.gstin || undefined,
        panNumber: val.panNumber || undefined,
        firstName: val.firstName!,
        lastName: val.lastName!,
        email: val.email!,
        phone: val.phone!,
        password: val.password!,
      })
      .subscribe({
        next: () => {
          this.isLoading.set(false);
          this.successMessage.set('Partner registration received successfully!');
          setTimeout(() => this.router.navigate(['/auth/login']), 2500);
        },
        error: (err) => {
          this.isLoading.set(false);
          this.errorMessage.set(err.error?.detail || 'Failed to submit partner registration. Please try again.');
        },
      });
  }
}
