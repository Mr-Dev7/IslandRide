import { Routes } from '@angular/router';
import { PublicLayoutComponent } from './layouts/public-layout/public-layout.component';
import { TouristLayoutComponent } from './layouts/tourist-layout/tourist-layout.component';
import { PartnerLayoutComponent } from './layouts/partner-layout/partner-layout.component';
import { AdminLayoutComponent } from './layouts/admin-layout/admin-layout.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  // Public Portal Layout
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
      }
    ]
  },

  // Tourist Dashboard Layout (Protected)
  {
    path: 'tourist',
    component: TouristLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'bookings',
        pathMatch: 'full'
      },
      {
        path: 'bookings',
        loadComponent: () => import('./features/tourist-dashboard/tourist-dashboard.component').then(m => m.TouristDashboardComponent)
      }
    ]
  },

  // Partner Console Layout (Protected)
  {
    path: 'partner',
    component: PartnerLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/partner-dashboard/partner-dashboard.component').then(m => m.PartnerDashboardComponent)
      }
    ]
  },

  // Admin Governance Layout (Protected)
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent)
      }
    ]
  },

  // Catch-all Redirect
  {
    path: '**',
    redirectTo: ''
  }
];
