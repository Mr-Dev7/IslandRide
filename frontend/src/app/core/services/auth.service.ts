import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, tap } from 'rxjs';
import {
  AuthResponse,
  BusinessDto,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  PartnerRegisterRequest,
  RegisterRequest,
  ResetPasswordRequest,
  UserDto,
} from '../models/auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // In-Memory Token & State (NEVER stored in localStorage / sessionStorage to prevent XSS)
  private readonly accessTokenSignal = signal<string | null>(null);
  readonly currentUser = signal<UserDto | null>(null);
  readonly isInitialized = signal<boolean>(false);

  readonly isAuthenticated = computed(() => !!this.currentUser());
  readonly isTourist = computed(() =>
    this.currentUser()?.roles?.includes('ROLE_TOURIST') ?? false
  );
  readonly isPartner = computed(() =>
    this.currentUser()?.roles?.some((r) => r.startsWith('ROLE_PARTNER')) ?? false
  );
  readonly isAdmin = computed(() =>
    this.currentUser()?.roles?.some(
      (r) => r.includes('ADMIN') || r === 'ROLE_SUPPORT_AGENT'
    ) ?? false
  );

  getAccessToken(): string | null {
    return this.accessTokenSignal();
  }

  /**
   * Restores session on browser refresh/startup via HttpOnly refresh cookie.
   */
  initAuth(): Observable<boolean> {
    return this.http
      .post<AuthResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap((res) => {
          this.accessTokenSignal.set(res.accessToken);
          this.currentUser.set(res.user);
          this.isInitialized.set(true);
        }),
        map(() => true),
        catchError(() => {
          this.accessTokenSignal.set(null);
          this.currentUser.set(null);
          this.isInitialized.set(true);
          return of(false);
        })
      );
  }

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/v1/auth/login', req, { withCredentials: true })
      .pipe(
        tap((res) => {
          this.accessTokenSignal.set(res.accessToken);
          this.currentUser.set(res.user);
        })
      );
  }

  register(req: RegisterRequest): Observable<UserDto> {
    return this.http.post<UserDto>('/api/v1/auth/register', req);
  }

  registerPartner(req: PartnerRegisterRequest): Observable<BusinessDto> {
    return this.http.post<BusinessDto>('/api/v1/partners/register', req);
  }

  refreshToken(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap((res) => {
          this.accessTokenSignal.set(res.accessToken);
          this.currentUser.set(res.user);
        })
      );
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/v1/auth/logout', {}, { withCredentials: true })
      .pipe(
        tap(() => {
          this.accessTokenSignal.set(null);
          this.currentUser.set(null);
          this.router.navigate(['/']);
        }),
        catchError(() => {
          this.accessTokenSignal.set(null);
          this.currentUser.set(null);
          this.router.navigate(['/']);
          return of(void 0);
        })
      );
  }

  forgotPassword(req: ForgotPasswordRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/auth/forgot-password', req);
  }

  resetPassword(req: ResetPasswordRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/auth/reset-password', req);
  }

  changePassword(req: ChangePasswordRequest): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/auth/change-password', req);
  }

  initCsrf(): Observable<void> {
    return this.http.get<void>('/api/v1/auth/csrf', { withCredentials: true });
  }
}

