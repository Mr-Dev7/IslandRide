import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'isInitialized', 'initAuth']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('should allow access if user is already authenticated', () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/tourist/bookings' } as RouterStateSnapshot)
    );

    expect(result).toBeTrue();
  });

  it('should redirect to /auth/login if unauthenticated and already initialized', () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.isInitialized.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/tourist/bookings' } as RouterStateSnapshot)
    );

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/tourist/bookings' },
    });
  });

  it('should attempt silent session restore on initAuth if not initialized', (done) => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.isInitialized.and.returnValue(false);
    authServiceSpy.initAuth.and.returnValue(of(true));

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/partner' } as RouterStateSnapshot)
    );

    if (typeof result === 'object' && 'subscribe' in result) {
      result.subscribe((allowed) => {
        expect(allowed).toBeTrue();
        done();
      });
    }
  });
});
