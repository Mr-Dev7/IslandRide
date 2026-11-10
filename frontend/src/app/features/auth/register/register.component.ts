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
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="text-center text-3xl font-extrabold text-slate-900 tracking-tight">
          Create Tourist Account
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Sign up to explore Andaman & Nicobar on two wheels
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-slate-200">
          @if (errorMessage()) {
            <div class="mb-4 p-3 bg-red-50 border-l-4 border-red-500 text-red-700 text-sm">
              {{ errorMessage() }}
            </div>
          }
          @if (successMessage()) {
            <div class="mb-4 p-3 bg-emerald-50 border-l-4 border-emerald-500 text-emerald-700 text-sm">
              {{ successMessage() }}
            </div>
          }

          <form [formGroup]="registerForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-slate-700">First Name</label>
                <input
                  type="text"
                  formControlName="firstName"
                  required
                  class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-slate-700">Last Name</label>
                <input
                  type="text"
                  formControlName="lastName"
                  required
                  class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                />
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">Email Address</label>
              <input
                type="email"
                formControlName="email"
                required
                class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">Phone (optional)</label>
              <input
                type="tel"
                formControlName="phone"
                placeholder="+919876543210"
                class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">Password</label>
              <input
                type="password"
                formControlName="password"
                required
                class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
              />
              <p class="mt-1 text-xs text-slate-500">Minimum 8 characters</p>
            </div>

            <div>
              <button
                type="submit"
                [disabled]="registerForm.invalid || isLoading()"
                class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50"
              >
                @if (isLoading()) {
                  <span>Creating account...</span>
                } @else {
                  <span>Create Account</span>
                }
              </button>
            </div>
          </form>

          <div class="mt-6 border-t border-slate-200 pt-6 text-center text-sm">
            <p class="text-slate-600">
              Already have an account?
              <a routerLink="/auth/login" class="font-medium text-emerald-600 hover:text-emerald-500">
                Sign in
              </a>
            </p>
          </div>
        </div>
      </div>
    </div>
  `,
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
