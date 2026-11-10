import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-access-denied',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div class="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-red-100">
          <svg class="h-8 w-8 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h2 class="mt-4 text-3xl font-extrabold text-slate-900 tracking-tight">
          Access Denied
        </h2>
        <p class="mt-2 text-sm text-slate-600">
          You do not possess the required permissions or role to access this portal or resource.
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-6 px-6 shadow sm:rounded-lg border border-slate-200 text-center space-y-4">
          <div class="text-sm text-slate-500">
            Current account: <span class="font-semibold text-slate-800">{{ authService.currentUser()?.email || 'Anonymous' }}</span>
          </div>

          <div class="flex flex-col sm:flex-row gap-3 justify-center">
            <button
              (click)="goHome()"
              class="w-full sm:w-auto inline-flex justify-center items-center px-4 py-2 border border-slate-300 shadow-sm text-sm font-medium rounded-md text-slate-700 bg-white hover:bg-slate-50"
            >
              Return Home
            </button>
            <button
              (click)="logout()"
              class="w-full sm:w-auto inline-flex justify-center items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700"
            >
              Sign Out
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class AccessDeniedComponent {
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  goHome(): void {
    this.router.navigate(['/']);
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/auth/login']);
    });
  }
}
