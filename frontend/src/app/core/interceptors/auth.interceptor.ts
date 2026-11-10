import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';

/**
 * Attaches the in-memory JWT Access Token to outgoing API requests.
 * Transparently rotates tokens via the HttpOnly refresh cookie upon 401 Unauthorized responses.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  let clonedReq = req;

  // Attach Bearer token if token exists and not hitting unauthenticated auth endpoints
  if (
    token &&
    req.url.startsWith('/api/') &&
    !req.url.includes('/api/v1/auth/login') &&
    !req.url.includes('/api/v1/auth/register') &&
    !req.url.includes('/api/v1/partners/register')
  ) {
    clonedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(clonedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If 401 on protected endpoint, attempt silent refresh via HttpOnly cookie
      if (error.status === 401 && !req.url.includes('/api/v1/auth/')) {
        return authService.refreshToken().pipe(
          switchMap((authRes) => {
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${authRes.accessToken}`,
              },
            });
            return next(retryReq);
          }),
          catchError((refreshErr) => {
            authService.logout();
            return throwError(() => refreshErr);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
