import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminFinanceOverviewDto,
  BookingDetailDto,
  BookingPage,
  BookingStatus,
  DisruptionExceptionType,
  OverbookingExceptionDto,
  RefundSummaryDto,
  RefundType,
} from '../models/booking.models';

@Injectable({
  providedIn: 'root',
})
export class AdminBookingFinanceService {
  private readonly http = inject(HttpClient);

  listBookings(page = 0, size = 20): Observable<BookingPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<BookingPage>('/api/v1/admin/bookings', { params });
  }

  getBooking(bookingPublicId: string): Observable<BookingDetailDto> {
    return this.http.get<BookingDetailDto>(`/api/v1/admin/bookings/${bookingPublicId}`);
  }

  applyDisruptionOverride(
    bookingPublicId: string,
    payload: { disruptionType: DisruptionExceptionType; reason: string; preferReschedule: boolean }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/admin/bookings/${bookingPublicId}/disruption-override`, payload);
  }

  resolveDispute(
    bookingPublicId: string,
    payload: {
      targetStatus: BookingStatus;
      resolutionNotes: string;
      refundAmount?: number | null;
      depositForfeitAmount?: number | null;
    }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/admin/bookings/${bookingPublicId}/dispute/resolve`, payload);
  }

  listOverbookingExceptions(status?: string): Observable<OverbookingExceptionDto[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<OverbookingExceptionDto[]>('/api/v1/admin/overbooking-exceptions', { params });
  }

  resolveOverbookingException(exceptionPublicId: string, resolutionNotes: string): Observable<OverbookingExceptionDto> {
    return this.http.post<OverbookingExceptionDto>(`/api/v1/admin/overbooking-exceptions/${exceptionPublicId}/resolve`, {
      resolutionNotes,
    });
  }

  getFinanceOverview(): Observable<AdminFinanceOverviewDto> {
    return this.http.get<AdminFinanceOverviewDto>('/api/v1/admin/finance/overview');
  }

  issueManualRefund(
    bookingPublicId: string,
    payload: { amount: number; reason: string; refundType?: RefundType },
    idempotencyKey?: string
  ): Observable<RefundSummaryDto> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<RefundSummaryDto>(`/api/v1/admin/finance/refunds/manual/${bookingPublicId}`, payload, {
      headers,
    });
  }

  retryRefund(refundPublicId: string, forceMarkReconciled = false): Observable<RefundSummaryDto> {
    const params = new HttpParams().set('forceMarkReconciled', String(forceMarkReconciled));
    return this.http.post<RefundSummaryDto>(`/api/v1/admin/finance/refunds/${refundPublicId}/retry`, {}, { params });
  }

  triggerPaymentReconciliation(): Observable<{ status: string; reconciledPaymentsCount: number }> {
    return this.http.post<{ status: string; reconciledPaymentsCount: number }>(
      '/api/v1/admin/finance/reconcile-payments',
      {}
    );
  }

  triggerBookingExpirationSweep(): Observable<{ status: string; expiredBookingsCount: number }> {
    return this.http.post<{ status: string; expiredBookingsCount: number }>(
      '/api/v1/admin/bookings/expire-sweep',
      {}
    );
  }
}
