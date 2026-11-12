import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PartnerBookingService } from '../../core/services/partner-booking.service';
import { BookingSummaryDto } from '../../core/models/booking.models';

@Component({
  selector: 'app-partner-bookings-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="partner-bookings-page">
      <div class="page-header">
        <div>
          <h2>Fleet Reservations & Handovers</h2>
          <p class="muted">Manage customer rental requests, handovers, cash/UPI balance collections and returns</p>
        </div>
        <div class="filter-row">
          <input
            type="text"
            placeholder="Filter by Business Public ID..."
            [(ngModel)]="businessFilter"
            (change)="fetchBookings()"
            class="biz-filter-input"
          />
          <button type="button" class="btn btn-primary" (click)="fetchBookings()">Refresh</button>
        </div>
      </div>

      @if (loading()) {
        <div class="card pad">Loading fleet reservations...</div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (bookings().length === 0) {
        <div class="card pad empty-box">
          <h3>No Fleet Bookings Found</h3>
          <p class="muted">No customer reservations have been placed yet for your fleet.</p>
        </div>
      } @else {
        <div class="table-container card">
          <table class="data-table">
            <thead>
              <tr>
                <th>Booking Ref</th>
                <th>Vehicle</th>
                <th>Customer</th>
                <th>Dates</th>
                <th>Total / Balance Due</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (b of bookings(); track b.publicId) {
                <tr>
                  <td>
                    <strong>{{ b.bookingReference }}</strong>
                    <div class="small muted">{{ b.createdAt | date: 'shortDate' }}</div>
                  </td>
                  <td>
                    <strong>{{ b.vehicleTitle }}</strong>
                    <div class="small muted">{{ b.partnerTradeName }}</div>
                  </td>
                  <td>
                    <div>{{ b.customerName || 'Tourist' }}</div>
                    <div class="small muted">{{ b.customerEmail }}</div>
                  </td>
                  <td>
                    <div class="small"><strong>Start:</strong> {{ b.startTime | date: 'short' }}</div>
                    <div class="small"><strong>End:</strong> {{ b.endTime | date: 'short' }}</div>
                    <div class="small muted">({{ b.totalDays }} days)</div>
                  </td>
                  <td>
                    <div>₹{{ b.totalPayableAmount | number: '1.2-2' }}</div>
                    @if (b.pickupBalanceDue > 0) {
                      <div class="small balance-due">Due: ₹{{ b.pickupBalanceDue | number: '1.2-2' }}</div>
                    } @else {
                      <div class="small text-success">Paid in Full</div>
                    }
                  </td>
                  <td>
                    <span class="badge" [ngClass]="badgeClass(b.status)">{{ b.status }}</span>
                    @if (b.status === 'AWAITING_PARTNER_APPROVAL' && b.approvalExpiresAt) {
                      <div class="small text-danger mt-xs">Expires: {{ b.approvalExpiresAt | date: 'shortTime' }}</div>
                    }
                  </td>
                  <td>
                    <a [routerLink]="['/partner/bookings', b.publicId]" class="btn btn-outline btn-sm">
                      Inspect & Act →
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .partner-bookings-page { max-width: 1150px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem; }
    .filter-row { display: flex; gap: 0.5rem; align-items: center; }
    .biz-filter-input { padding: 0.45rem 0.75rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.875rem; width: 220px; }
    .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; }
    .pad { padding: 1.5rem; }
    .empty-box { text-align: center; padding: 3rem 1.5rem; }
    .table-container { overflow-x: auto; }
    .data-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    .data-table th, .data-table td { padding: 0.75rem 1rem; border-bottom: 1px solid #e2e8f0; text-align: left; }
    .data-table th { background: #f8fafc; font-weight: 600; color: #475569; }
    .balance-due { color: #d97706; font-weight: 600; }
    .text-success { color: #16a34a; font-weight: 500; }
    .text-danger { color: #dc2626; }
    .badge { display: inline-block; font-size: 0.75rem; padding: 2px 8px; border-radius: 999px; font-weight: 600; }
    .badge-success { background: #dcfce7; color: #166534; }
    .badge-warning { background: #fef3c7; color: #92400e; }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-danger { background: #fee2e2; color: #991b1b; }
    .badge-neutral { background: #f1f5f9; color: #475569; }
    .btn { padding: 0.45rem 0.9rem; border-radius: 6px; font-weight: 600; cursor: pointer; border: none; font-size: 0.8125rem; }
    .btn-primary { background: #0284c7; color: #fff; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #0284c7; text-decoration: none; }
    .btn-outline:hover { background: #f0f9ff; border-color: #0284c7; }
    .btn-sm { padding: 0.35rem 0.65rem; }
    .small { font-size: 0.75rem; }
    .muted { color: #64748b; }
    .mt-xs { margin-top: 0.2rem; }
    .alert-error { background: #fee2e2; color: #991b1b; padding: 0.75rem; border-radius: 6px; }
  `]
})
export class PartnerBookingsListComponent implements OnInit {
  private readonly partnerBookingService = inject(PartnerBookingService);

  bookings = signal<BookingSummaryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  businessFilter = '';

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
    this.loading.set(true);
    this.partnerBookingService.listBookings(this.businessFilter.trim() || undefined, 0, 30).subscribe({
      next: (res) => {
        this.bookings.set(res.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.message || 'Failed to load fleet bookings.');
        this.loading.set(false);
      },
    });
  }

  badgeClass(status: string): string {
    switch (status) {
      case 'CONFIRMED':
      case 'COMPLETED':
        return 'badge-success';
      case 'ACTIVE':
        return 'badge-info';
      case 'AWAITING_PARTNER_APPROVAL':
      case 'APPROVED':
      case 'PENDING_PAYMENT':
        return 'badge-warning';
      case 'CANCELLED':
      case 'REJECTED':
      case 'EXPIRED':
      case 'NO_SHOW':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }
}
