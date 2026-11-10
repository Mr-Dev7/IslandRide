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
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="text-center text-3xl font-extrabold text-slate-900 tracking-tight">
          IslandRide Portal Login
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Access your Tourist, Partner, or Governance console
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-slate-200">
          @if (errorMessage()) {
            <div class="mb-4 p-3 bg-red-50 border-l-4 border-red-500 text-red-700 text-sm">
              {{ errorMessage() }}
            </div>
          }

          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="space-y-6">
            <div>
              <label for="email" class="block text-sm font-medium text-slate-700">Email address</label>
              <div class="mt-1">
                <input
                  id="email"
                  type="email"
                  formControlName="email"
                  autocomplete="email"
                  required
                  class="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm placeholder-slate-400 focus:outline-none focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm"
                  placeholder="name@example.com"
                />
              </div>
            </div>

            <div>
              <label for="password" class="block text-sm font-medium text-slate-700">Password</label>
              <div class="mt-1">
                <input
                  id="password"
                  type="password"
                  formControlName="password"
                  autocomplete="current-password"
                  required
                  class="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm placeholder-slate-400 focus:outline-none focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm"
                />
              </div>
            </div>

            <div class="flex items-center justify-between">
              <div class="text-sm">
                <a routerLink="/auth/forgot-password" class="font-medium text-emerald-600 hover:text-emerald-500">
                  Forgot your password?
                </a>
              </div>
            </div>

            <div>
              <button
                type="submit"
                [disabled]="loginForm.invalid || isLoading()"
                class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 disabled:opacity-50"
              >
                @if (isLoading()) {
                  <span>Signing in...</span>
                } @else {
                  <span>Sign in</span>
                }
              </button>
            </div>
          </form>

          <div class="mt-6 border-t border-slate-200 pt-6 text-center text-sm">
            <p class="text-slate-600">
              New tourist?
              <a routerLink="/auth/register" class="font-medium text-emerald-600 hover:text-emerald-500">
                Create an account
              </a>
            </p>
            <p class="mt-2 text-slate-600">
              Vehicle rental agency?
              <a routerLink="/partner/register" class="font-medium text-emerald-600 hover:text-emerald-500">
                Partner Onboarding
              </a>
            </p>
          </div>
        </div>
      </div>
    </div>
  `,
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

        // Navigate based on primary role
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

