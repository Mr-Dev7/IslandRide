import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layouts/public-layout/public-layout.component';
import { TouristLayoutComponent } from './layouts/tourist-layout/tourist-layout.component';
import { PartnerLayoutComponent } from './layouts/partner-layout/partner-layout.component';
import { AdminLayoutComponent } from './layouts/admin-layout/admin-layout.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  // Public Portal Layout
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'auth/login',
        loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'auth/register',
        loadComponent: () => import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
      },
      {
        path: 'auth/forgot-password',
        loadComponent: () =>
          import('./features/auth/forgot-password/forgot-password.component').then(
            (m) => m.ForgotPasswordComponent
          ),
      },
      {
        path: 'auth/reset-password',
        loadComponent: () =>
          import('./features/auth/reset-password/reset-password.component').then(
            (m) => m.ResetPasswordComponent
          ),
      },
      {
        path: 'auth/access-denied',
        loadComponent: () =>
          import('./features/auth/access-denied/access-denied.component').then((m) => m.AccessDeniedComponent),
      },
      {
        path: 'partner/register',
        loadComponent: () =>
          import('./features/partner/register/partner-register.component').then(
            (m) => m.PartnerRegisterComponent
          ),
      },
    ],
  },

  // Tourist Dashboard Layout (Protected)
  {
    path: 'tourist',
    component: TouristLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_TOURIST'] },
    children: [
      {
        path: '',
        redirectTo: 'bookings',
        pathMatch: 'full',
      },
      {
        path: 'bookings',
        loadComponent: () =>
          import('./features/tourist-dashboard/tourist-dashboard.component').then(
            (m) => m.TouristDashboardComponent
          ),
      },
    ],
  },

  // Partner Console Layout (Protected)
  {
    path: 'partner',
    component: PartnerLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_PARTNER_OWNER', 'ROLE_PARTNER_MANAGER', 'ROLE_PARTNER_STAFF'] },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/partner-dashboard/partner-dashboard.component').then(
            (m) => m.PartnerDashboardComponent
          ),
      },
    ],
  },

  // Admin Governance Layout (Protected)
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_SUPER_ADMIN', 'ROLE_OPERATIONS_ADMIN', 'ROLE_FINANCE_ADMIN', 'ROLE_SUPPORT_AGENT'],
    },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin-dashboard/admin-dashboard.component').then(
            (m) => m.AdminDashboardComponent
          ),
      },
    ],
  },

  // Catch-all Redirect
  {
    path: '**',
    redirectTo: '',
  },
];
