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
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="text-center text-3xl font-extrabold text-slate-900 tracking-tight">
          Reset Your Password
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Enter your registered email address to receive password reset instructions
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-slate-200">
          @if (submitted()) {
            <div class="p-4 bg-emerald-50 border-l-4 border-emerald-500 text-emerald-800 text-sm">
              If an account exists with this email, a secure password reset link has been dispatched. Please check your inbox.
            </div>
            <div class="mt-6 text-center">
              <a routerLink="/auth/reset-password" class="font-medium text-emerald-600 hover:text-emerald-500 text-sm">
                Already have a reset token? Enter it here &rarr;
              </a>
            </div>
          } @else {
            <form [formGroup]="forgotForm" (ngSubmit)="onSubmit()" class="space-y-6">
              <div>
                <label class="block text-sm font-medium text-slate-700">Email Address</label>
                <input
                  type="email"
                  formControlName="email"
                  required
                  placeholder="name@example.com"
                  class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm sm:text-sm focus:ring-emerald-500 focus:border-emerald-500"
                />
              </div>

              <div>
                <button
                  type="submit"
                  [disabled]="forgotForm.invalid || isLoading()"
                  class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50"
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
        // Obfuscate whether email exists for anti-enumeration
        this.isLoading.set(false);
        this.submitted.set(true);
      },
    });
  }
}

