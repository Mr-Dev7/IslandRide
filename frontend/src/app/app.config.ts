import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { HttpClient, provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { catchError, firstValueFrom, of, switchMap } from 'rxjs';
import { routes } from './app.routes';
import { correlationIdInterceptor } from './core/interceptors/correlation-id.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([correlationIdInterceptor, authInterceptor]),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      })
    ),
    provideAppInitializer(() => {
      const http = inject(HttpClient);
      const authService = inject(AuthService);
      return firstValueFrom(
        http.get<void>('/api/v1/auth/csrf', { withCredentials: true }).pipe(
          catchError(() => of(null)),
          switchMap(() => authService.initAuth())
        )
      );
    }),
  ],
};
