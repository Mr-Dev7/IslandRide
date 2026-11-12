import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BookingService } from '../../../core/services/booking.service';
import { BookingSummaryDto } from '../../../core/models/booking.models';

@Component({
  selector: 'app-tourist-bookings',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="bookings-page">
      <div class="page-head">
        <div>
          <h1>My Vehicle Reservations</h1>
          <p class="muted">Track booking confirmations, balance due, driver instructions and receipts</p>
        </div>
        <a routerLink="/search" class="btn btn-primary">+ Rent Another Vehicle</a>
      </div>

      @if (loading()) {
        <div class="card pad">Loading reservations...</div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (bookings().length === 0) {
        <div class="card pad empty-state">
          <h3>No Reservations Yet</h3>
          <p class="muted">You haven't booked any scooters, bikes, or cars yet for your Andaman island trip.</p>
          <a routerLink="/search" class="btn btn-primary mt-sm">Explore Vehicle Fleet</a>
        </div>
      } @else {
        <div class="booking-list">
          @for (b of bookings(); track b.publicId) {
            <div class="card pad booking-card">
              <div class="card-top">
                <div>
                  <span class="ref-badge">{{ b.bookingReference }}</span>
                  <h3>{{ b.vehicleTitle }}</h3>
                  <span class="muted small">{{ b.partnerTradeName }}</span>
                </div>
                <div class="status-col">
                  <span class="badge" [ngClass]="badgeClass(b.status)">{{ formatStatus(b.status) }}</span>
                  <span class="payment-badge" [ngClass]="paymentBadgeClass(b.paymentSummaryStatus)">
                    {{ formatPaymentStatus(b.paymentSummaryStatus) }}
                  </span>
                </div>
              </div>

              <div class="card-details">
                <div class="detail-item">
                  <span class="muted small">Pickup</span>
                  <strong>{{ b.startTime | date: 'mediumDate' }}</strong>
                  <span class="small">{{ b.pickupLocationName || 'Island Hub' }}</span>
                </div>
                <div class="detail-item">
                  <span class="muted small">Return</span>
                  <strong>{{ b.endTime | date: 'mediumDate' }}</strong>
                  <span class="small">{{ b.returnLocationName || 'Island Hub' }}</span>
                </div>
                <div class="detail-item">
                  <span class="muted small">Total Rental</span>
                  <strong>₹{{ b.totalPayableAmount | number: '1.2-2' }}</strong>
                  <span class="small">{{ b.totalDays }} day(s)</span>
                </div>
                <div class="detail-item">
                  <span class="muted small">Balance Due at Pickup</span>
                  <strong [class.due-highlight]="b.pickupBalanceDue > 0">
                    ₹{{ b.pickupBalanceDue | number: '1.2-2' }}
                  </strong>
                  <span class="small">Deposit: ₹{{ b.securityDepositAmount | number: '1.0-0' }}</span>
                </div>
              </div>

              <div class="card-footer">
                @if (b.status === 'PENDING_PAYMENT' || b.status === 'APPROVED') {
                  <span class="deadline-note">
                    ⚠️ Complete payment before: <strong>{{ b.paymentDeadlineAt | date: 'shortTime' }}</strong>
                  </span>
                }
                <div class="actions">
                  <a [routerLink]="['/tourist/bookings', b.publicId]" class="btn btn-outline">
                    View Details & Actions →
                  </a>
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .bookings-page { max-width: 950px; margin: 0 auto; }
    .page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; }
    .pad { padding: 1.25rem 1.5rem; }
    .empty-state { text-align: center; padding: 3rem 1.5rem; }
    .booking-list { display: flex; flex-direction: column; gap: 1rem; }
    .booking-card { display: flex; flex-direction: column; gap: 1rem; }
    .card-top { display: flex; justify-content: space-between; align-items: flex-start; }
    .ref-badge { display: inline-block; font-size: 0.75rem; background: #f1f5f9; color: #475569; padding: 2px 6px; border-radius: 4px; font-weight: 600; margin-bottom: 0.25rem; }
    .status-col { display: flex; flex-direction: column; gap: 0.35rem; align-items: flex-end; }
    .badge { font-size: 0.8125rem; padding: 3px 10px; border-radius: 999px; font-weight: 600; }
    .payment-badge { font-size: 0.75rem; padding: 2px 8px; border-radius: 4px; font-weight: 500; }
    .badge-success { background: #dcfce7; color: #166534; }
    .badge-warning { background: #fef3c7; color: #92400e; }
    .badge-info { background: #e0f2fe; color: #0369a1; }
    .badge-danger { background: #fee2e2; color: #991b1b; }
    .badge-neutral { background: #f1f5f9; color: #475569; }
    .pay-full { background: #dcfce7; color: #166534; }
    .pay-partial { background: #fef3c7; color: #92400e; }
    .pay-unpaid { background: #fee2e2; color: #991b1b; }
    .card-details { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; background: #f8fafc; padding: 0.85rem; border-radius: 6px; }
    .detail-item { display: flex; flex-direction: column; gap: 0.15rem; }
    .due-highlight { color: #d97706; }
    .card-footer { display: flex; justify-content: space-between; align-items: center; }
    .deadline-note { font-size: 0.8125rem; color: #b45309; }
    .btn { padding: 0.5rem 1rem; border-radius: 6px; font-weight: 600; text-decoration: none; display: inline-block; font-size: 0.875rem; cursor: pointer; border: none; }
    .btn-primary { background: #0284c7; color: #ffffff; }
    .btn-outline { background: transparent; border: 1px solid #cbd5e1; color: #0284c7; }
    .btn-outline:hover { background: #f0f9ff; border-color: #0284c7; }
    .muted { color: #64748b; }
    .small { font-size: 0.8125rem; }
    .mt-sm { margin-top: 0.5rem; }
  `]
})
export class TouristBookingsComponent implements OnInit {
  private readonly bookingService = inject(BookingService);

  bookings = signal<BookingSummaryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.fetchBookings();
  }

  fetchBookings(): void {
    this.bookingService.listMyBookings(0, 20).subscribe({
      next: (res) => {
        this.bookings.set(res.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || err?.message || 'Failed to load reservations.');
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

  paymentBadgeClass(payStatus: string): string {
    switch (payStatus) {
      case 'PAID_IN_FULL':
        return 'pay-full';
      case 'PARTIALLY_PAID':
        return 'pay-partial';
      default:
        return 'pay-unpaid';
    }
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ');
  }

  formatPaymentStatus(status: string): string {
    return status.replace(/_/g, ' ');
  }
}
