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
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="text-center text-3xl font-extrabold text-slate-900 tracking-tight">
          Set New Password
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Enter your recovery token and new account password
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

          <form [formGroup]="resetForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-slate-700">Reset Token</label>
              <input
                type="text"
                formControlName="token"
                required
                placeholder="UUID or token from email"
                class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
              />
            </div>

            <div>
              <label class="block text-sm font-medium text-slate-700">New Password</label>
              <input
                type="password"
                formControlName="newPassword"
                required
                class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
              />
              <p class="mt-1 text-xs text-slate-500">Minimum 8 characters</p>
            </div>

            <div>
              <button
                type="submit"
                [disabled]="resetForm.invalid || isLoading()"
                class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50"
              >
                @if (isLoading()) {
                  <span>Updating password...</span>
                } @else {
                  <span>Update Password</span>
                }
              </button>
            </div>
          </form>

          <div class="mt-6 border-t border-slate-200 pt-6 text-center text-sm">
            <a routerLink="/auth/login" class="font-medium text-slate-600 hover:text-slate-900">
              &larr; Back to login
            </a>
          </div>
        </div>
      </div>
    </div>
  `,
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
