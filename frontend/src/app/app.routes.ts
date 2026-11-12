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
        path: 'search',
        loadComponent: () =>
          import('./features/catalog/vehicle-catalog.component').then((m) => m.VehicleCatalogComponent),
      },
      {
        path: 'vehicles/:publicId',
        loadComponent: () =>
          import('./features/catalog/vehicle-detail.component').then((m) => m.VehicleDetailComponent),
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
          import('./features/tourist/bookings/tourist-bookings.component').then(
            (m) => m.TouristBookingsComponent
          ),
      },
      {
        path: 'bookings/:publicId',
        loadComponent: () =>
          import('./features/tourist/bookings/tourist-booking-detail.component').then(
            (m) => m.TouristBookingDetailComponent
          ),
      },
      {
        path: 'checkout/:holdPublicId',
        loadComponent: () =>
          import('./features/tourist/checkout/tourist-checkout.component').then(
            (m) => m.TouristCheckoutComponent
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
      {
        path: 'fleet',
        loadComponent: () =>
          import('./features/partner-fleet/partner-fleet-list.component').then(
            (m) => m.PartnerFleetListComponent
          ),
      },
      {
        path: 'fleet/new',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_PARTNER_OWNER', 'ROLE_PARTNER_MANAGER'] },
        loadComponent: () =>
          import('./features/partner-fleet/partner-vehicle-form.component').then(
            (m) => m.PartnerVehicleFormComponent
          ),
      },
      {
        path: 'fleet/:publicId/edit',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_PARTNER_OWNER', 'ROLE_PARTNER_MANAGER'] },
        loadComponent: () =>
          import('./features/partner-fleet/partner-vehicle-form.component').then(
            (m) => m.PartnerVehicleFormComponent
          ),
      },
      {
        path: 'fleet/:publicId',
        loadComponent: () =>
          import('./features/partner-fleet/partner-vehicle-manage.component').then(
            (m) => m.PartnerVehicleManageComponent
          ),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/partner-fleet/partner-calendar-hubs.component').then(
            (m) => m.PartnerCalendarHubsComponent
          ),
      },
      {
        path: 'bookings',
        loadComponent: () =>
          import('./features/partner-fleet/partner-bookings-list.component').then(
            (m) => m.PartnerBookingsListComponent
          ),
      },
      {
        path: 'bookings/:publicId',
        loadComponent: () =>
          import('./features/partner-fleet/partner-booking-detail.component').then(
            (m) => m.PartnerBookingDetailComponent
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
      {
        path: 'fleet',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_SUPER_ADMIN', 'ROLE_OPERATIONS_ADMIN'] },
        loadComponent: () =>
          import('./features/admin-fleet/admin-moderation-queue.component').then(
            (m) => m.AdminModerationQueueComponent
          ),
      },
      {
        path: 'fleet/:publicId',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_SUPER_ADMIN', 'ROLE_OPERATIONS_ADMIN'] },
        loadComponent: () =>
          import('./features/admin-fleet/admin-vehicle-review.component').then(
            (m) => m.AdminVehicleReviewComponent
          ),
      },
      {
        path: 'catalog-config',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_SUPER_ADMIN', 'ROLE_OPERATIONS_ADMIN'] },
        loadComponent: () =>
          import('./features/admin-fleet/admin-catalog-config.component').then(
            (m) => m.AdminCatalogConfigComponent
          ),
      },
      {
        path: 'bookings',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_SUPER_ADMIN', 'ROLE_OPERATIONS_ADMIN', 'ROLE_SUPPORT_AGENT'] },
        loadComponent: () =>
          import('./features/admin-fleet/admin-bookings.component').then(
            (m) => m.AdminBookingsComponent
          ),
      },
      {
        path: 'finance',
        canActivate: [roleGuard],
        data: { roles: ['ROLE_SUPER_ADMIN', 'ROLE_FINANCE_ADMIN'] },
        loadComponent: () =>
          import('./features/admin-fleet/admin-finance.component').then(
            (m) => m.AdminFinanceComponent
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
