import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse, UserDto } from '../models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  const mockUser: UserDto = {
    publicId: 'usr_123',
    email: 'tourist@islandride.in',
    firstName: 'Anand',
    lastName: 'Sharma',
    status: 'ACTIVE',
    roles: ['ROLE_TOURIST'],
  };

  const mockAuthResponse: AuthResponse = {
    accessToken: 'mock.jwt.token',
    tokenType: 'Bearer',
    expiresIn: 900000,
    user: mockUser,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created with empty in-memory credentials', () => {
    expect(service).toBeTruthy();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
  });

  it('should store access token in memory upon successful login', () => {
    service.login({ email: 'tourist@islandride.in', password: 'Password123!' }).subscribe((res) => {
      expect(res.accessToken).toEqual('mock.jwt.token');
    });

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.method).toEqual('POST');
    req.flush(mockAuthResponse);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getAccessToken()).toEqual('mock.jwt.token');
    expect(service.isTourist()).toBeTrue();
    expect(service.isAdmin()).toBeFalse();
    expect(service.isPartner()).toBeFalse();
  });

  it('should clear in-memory token and state upon logout', () => {
    // Seed logged in state
    service.login({ email: 'tourist@islandride.in', password: 'Password123!' }).subscribe();
    httpTesting.expectOne('/api/v1/auth/login').flush(mockAuthResponse);
    expect(service.isAuthenticated()).toBeTrue();

    service.logout().subscribe();
    const logoutReq = httpTesting.expectOne('/api/v1/auth/logout');
    expect(logoutReq.request.method).toEqual('POST');
    logoutReq.flush(null);

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAccessToken()).toBeNull();
  });

  it('should restore session via HttpOnly refresh token on initAuth', () => {
    service.initAuth().subscribe((success) => {
      expect(success).toBeTrue();
    });

    const req = httpTesting.expectOne('/api/v1/auth/refresh');
    expect(req.request.method).toEqual('POST');
    req.flush(mockAuthResponse);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getAccessToken()).toEqual('mock.jwt.token');
  });
});
