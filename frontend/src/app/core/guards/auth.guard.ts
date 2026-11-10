import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * Foundation Auth Guard.
 * Provides client navigation UX only. Backend authorization remains strictly authoritative.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  // Foundation placeholder for Phase 2; Phase 4 integrates real session state
  const isAuthenticated = true;

  if (!isAuthenticated) {
    router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }
  return true;
};

