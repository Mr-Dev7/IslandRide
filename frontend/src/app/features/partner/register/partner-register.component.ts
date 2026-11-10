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
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-xl">
        <h2 class="text-center text-3xl font-extrabold text-slate-900 tracking-tight">
          Partner Vendor Onboarding
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Register your Andaman vehicle rental business on IslandRide
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-xl">
        <div class="bg-white py-8 px-6 shadow sm:rounded-lg sm:px-10 border border-slate-200">
          @if (errorMessage()) {
            <div class="mb-4 p-3 bg-red-50 border-l-4 border-red-500 text-red-700 text-sm">
              {{ errorMessage() }}
            </div>
          }
          @if (successMessage()) {
            <div class="mb-4 p-4 bg-emerald-50 border-l-4 border-emerald-500 text-emerald-800 text-sm space-y-2">
              <p class="font-bold">{{ successMessage() }}</p>
              <p>Your business profile has been registered in <strong>DRAFT</strong> status. Our operations team will review your business credentials before inventory publishing is enabled.</p>
            </div>
          }

          <form [formGroup]="partnerForm" (ngSubmit)="onSubmit()" class="space-y-6">
            <div>
              <h3 class="text-md font-semibold text-slate-900 border-b pb-2 mb-4">1. Business Profile</h3>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium text-slate-700">Legal Business Name *</label>
                  <input
                    type="text"
                    formControlName="legalName"
                    placeholder="e.g. Andaman Island Rentals Pvt Ltd"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">Trade / Brand Name *</label>
                  <input
                    type="text"
                    formControlName="tradeName"
                    placeholder="e.g. Island Wheels Port Blair"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">GSTIN (optional)</label>
                  <input
                    type="text"
                    formControlName="gstin"
                    placeholder="15-character GSTIN"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">PAN Number (optional)</label>
                  <input
                    type="text"
                    formControlName="panNumber"
                    placeholder="10-character PAN"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
              </div>
            </div>

            <div>
              <h3 class="text-md font-semibold text-slate-900 border-b pb-2 mb-4">2. Authorized Owner Account</h3>
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium text-slate-700">First Name *</label>
                  <input
                    type="text"
                    formControlName="firstName"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">Last Name *</label>
                  <input
                    type="text"
                    formControlName="lastName"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">Owner Email Address *</label>
                  <input
                    type="email"
                    formControlName="email"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-slate-700">Owner Contact Phone *</label>
                  <input
                    type="tel"
                    formControlName="phone"
                    placeholder="+919876543210"
                    class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                  />
                </div>
              </div>

              <div class="mt-4">
                <label class="block text-sm font-medium text-slate-700">Password *</label>
                <input
                  type="password"
                  formControlName="password"
                  class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                />
                <p class="mt-1 text-xs text-slate-500">Minimum 8 characters</p>
              </div>
            </div>

            <div>
              <button
                type="submit"
                [disabled]="partnerForm.invalid || isLoading()"
                class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50"
              >
                @if (isLoading()) {
                  <span>Registering partner business...</span>
                } @else {
                  <span>Submit Partner Registration</span>
                }
              </button>
            </div>
          </form>

          <div class="mt-6 border-t border-slate-200 pt-6 text-center text-sm">
            <p class="text-slate-600">
              Already registered?
              <a routerLink="/auth/login" class="font-medium text-emerald-600 hover:text-emerald-500">
                Sign in to Partner Console
              </a>
            </p>
          </div>
        </div>
      </div>
    </div>
  `,
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

