import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Client-Side Auth Guard for navigation UX.
 * Backend Spring Security authorization remains strictly authoritative.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // If not yet initialized, attempt silent session restoration from HttpOnly refresh cookie
  if (!authService.isInitialized()) {
    return authService.initAuth().pipe(
      map((isAuth) => {
        if (isAuth) {
          return true;
        }
        router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
        return false;
      })
    );
  }

  router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
  return false;
};
