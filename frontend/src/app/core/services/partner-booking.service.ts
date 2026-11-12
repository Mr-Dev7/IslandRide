import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BookingDetailDto, BookingPage } from '../models/booking.models';

@Injectable({
  providedIn: 'root',
})
export class PartnerBookingService {
  private readonly http = inject(HttpClient);

  listBookings(businessPublicId?: string, page = 0, size = 15): Observable<BookingPage> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (businessPublicId) {
      params = params.set('businessPublicId', businessPublicId);
    }
    return this.http.get<BookingPage>('/api/v1/partners/bookings', { params });
  }

  getBooking(bookingPublicId: string): Observable<BookingDetailDto> {
    return this.http.get<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}`);
  }

  approveBooking(bookingPublicId: string, reason?: string): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/approve`, {
      reason: reason || null,
    });
  }

  rejectBooking(bookingPublicId: string, reason?: string): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/reject`, {
      reason: reason || null,
    });
  }

  recordPickupHandover(
    bookingPublicId: string,
    payload: {
      odometerReading: number;
      fuelPercentage: number;
      checklistDataJson?: string;
      notes?: string;
      balanceCollectedAmount?: number | null;
      balancePaymentMethod?: string | null;
      balanceReceiptReference?: string | null;
      depositCollectedAmount?: number | null;
      depositPaymentMethod?: string | null;
      depositReceiptReference?: string | null;
      depositWaived: boolean;
      evidence?: Array<{ evidenceType: string; imageS3Key: string; mediaMimeType?: string }>;
    }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/handover/pickup`, payload);
  }

  recordReturnCompletion(
    bookingPublicId: string,
    payload: {
      odometerReading: number;
      fuelPercentage: number;
      checklistDataJson?: string;
      notes?: string;
      damageReported: boolean;
      damageClaimAmount?: number | null;
      releaseDepositImmediately: boolean;
      evidence?: Array<{ evidenceType: string; imageS3Key: string; mediaMimeType?: string }>;
    }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/handover/return`, payload);
  }

  markNoShow(
    bookingPublicId: string,
    payload: { evidenceNotes?: string; evidenceRef?: string }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/no-show`, payload);
  }

  settleDeposit(
    bookingPublicId: string,
    payload: { releaseAmount?: number; forfeitAmount?: number; notes?: string }
  ): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/partners/bookings/${bookingPublicId}/deposit/settle`, payload);
  }
}
