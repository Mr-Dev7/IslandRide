import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { RoleType } from '../models/auth.models';

/**
 * Role-Based Access Control Guard for portal routing UX.
 */
export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const expectedRoles = (route.data?.['roles'] as RoleType[]) || [];
  const user = authService.currentUser();

  if (!user) {
    router.navigate(['/auth/login']);
    return false;
  }

  const hasRole = user.roles?.some((r) => expectedRoles.includes(r));
  if (!hasRole) {
    router.navigate(['/auth/access-denied']);
    return false;
  }

  return true;
};
