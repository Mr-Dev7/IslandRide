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
      <div class="page-head">
        <div>
          <h2>Fleet Reservations & Handovers</h2>
          <p class="muted">Manage customer rental requests, handovers, cash/UPI balance collections and returns</p>
        </div>
        <div class="actions-row">
          <button type="button" class="ir-btn ir-btn-outline" (click)="fetchBookings()">
            Refresh
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="ir-card pad">Loading fleet reservations...</div>
      } @else if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      } @else if (bookings().length === 0) {
        <div class="ir-card pad empty-box">
          <h3>No Fleet Bookings Found</h3>
          <p class="muted">No customer reservations have been placed yet for your fleet.</p>
        </div>
      } @else {
        <div class="ir-table-wrap">
          <table class="ir-table">
            <thead>
              <tr>
                <th>Booking Ref</th>
                <th>Vehicle</th>
                <th>Customer</th>
                <th>Schedule</th>
                <th>Total / Due</th>
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
                    <a [routerLink]="['/partner/bookings', b.publicId]" class="ir-btn ir-btn-sm ir-btn-outline">
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
    .partner-bookings-page { max-width: 1200px; margin: 0 auto; }
    .empty-box { text-align: center; padding: 3rem 1.5rem; }
    .balance-due { color: #d97706; font-weight: 600; }
    .text-success { color: #166534; font-weight: 500; }
    .text-danger { color: #991b1b; }
  `]
})
export class PartnerBookingsListComponent implements OnInit {
  private readonly partnerBookingService = inject(PartnerBookingService);

  bookings = signal<BookingSummaryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
    this.loading.set(true);
    this.partnerBookingService.listBookings(undefined, 0, 50).subscribe({
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
        return 'badge-warn';
      case 'CANCELLED':
      case 'REJECTED':
      case 'EXPIRED':
      case 'NO_SHOW':
        return 'badge-danger';
      default:
        return 'badge';
    }
  }
}
