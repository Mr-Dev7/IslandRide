import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AcquireHoldRequest,
  BookingCreationResult,
  BookingDetailDto,
  BookingPage,
  CancellationPreviewResponse,
  CreateBookingRequest,
  GatewayCheckoutOrderResponse,
  HoldResultDto,
  InventoryHoldResponse,
  MockPaymentSimulateResponse,
  PaymentMode,
  PaymentVerificationResponse,
  PricePreviewResponse,
} from '../models/booking.models';

@Injectable({
  providedIn: 'root',
})
export class BookingService {
  private readonly http = inject(HttpClient);

  getPublicQuote(
    vehiclePublicId: string,
    startTime: string,
    endTime: string,
    options?: {
      pickupLocationPublicId?: string | null;
      returnLocationPublicId?: string | null;
      paymentMode?: PaymentMode;
      deliveryRequested?: boolean;
    }
  ): Observable<PricePreviewResponse> {
    let params = new HttpParams()
      .set('startTime', startTime)
      .set('endTime', endTime);
    if (options?.pickupLocationPublicId) {
      params = params.set('pickupLocationPublicId', options.pickupLocationPublicId);
    }
    if (options?.returnLocationPublicId) {
      params = params.set('returnLocationPublicId', options.returnLocationPublicId);
    }
    if (options?.paymentMode) {
      params = params.set('paymentMode', options.paymentMode);
    }
    if (options?.deliveryRequested !== undefined) {
      params = params.set('deliveryRequested', String(options.deliveryRequested));
    }
    return this.http.get<PricePreviewResponse>(`/api/v1/public/bookings/quote/${vehiclePublicId}`, { params });
  }

  acquireHold(request: AcquireHoldRequest, idempotencyKey?: string): Observable<HoldResultDto> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<HoldResultDto>('/api/v1/tourist/holds', request, { headers });
  }

  getHold(holdPublicId: string): Observable<InventoryHoldResponse> {
    return this.http.get<InventoryHoldResponse>(`/api/v1/tourist/holds/${holdPublicId}`);
  }

  releaseHold(holdPublicId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/tourist/holds/${holdPublicId}`);
  }

  createBooking(request: CreateBookingRequest, idempotencyKey?: string): Observable<BookingCreationResult> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<BookingCreationResult>('/api/v1/tourist/bookings', request, { headers });
  }

  listMyBookings(page = 0, size = 10): Observable<BookingPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<BookingPage>('/api/v1/tourist/bookings', { params });
  }

  getMyBooking(bookingPublicId: string): Observable<BookingDetailDto> {
    return this.http.get<BookingDetailDto>(`/api/v1/tourist/bookings/${bookingPublicId}`);
  }

  initiateCheckoutOrder(bookingPublicId: string, idempotencyKey?: string): Observable<GatewayCheckoutOrderResponse> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<GatewayCheckoutOrderResponse>(
      `/api/v1/tourist/bookings/${bookingPublicId}/payment-order`,
      {},
      { headers }
    );
  }

  verifyPayment(
    payload: {
      bookingPublicId: string;
      gatewayOrderId: string;
      gatewayPaymentId: string;
      gatewaySignature: string;
    },
    idempotencyKey?: string
  ): Observable<PaymentVerificationResponse> {
    const headers = idempotencyKey ? new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) : undefined;
    return this.http.post<PaymentVerificationResponse>('/api/v1/tourist/payments/verify', payload, { headers });
  }

  simulateMockPayment(
    gatewayOrderId: string,
    outcome: 'CAPTURE_SUCCESS' | 'PAYMENT_FAILED' | 'WEBHOOK_CAPTURE',
    failureReason?: string
  ): Observable<MockPaymentSimulateResponse> {
    return this.http.post<MockPaymentSimulateResponse>('/api/v1/payments/mock/simulate', {
      gatewayOrderId,
      outcome,
      failureReason: failureReason || null,
    });
  }

  sendMockWebhook(
    provider: string,
    rawPayload: string,
    signature: string,
    eventId?: string
  ): Observable<{ accepted: boolean; duplicate: boolean; signatureValid: boolean; message: string }> {
    let headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'X-Razorpay-Signature': signature,
    });
    if (eventId) {
      headers = headers.set('X-Razorpay-Event-Id', eventId);
    }
    return this.http.post<{ accepted: boolean; duplicate: boolean; signatureValid: boolean; message: string }>(
      `/api/v1/payments/webhooks/${provider}`,
      rawPayload,
      { headers }
    );
  }

  modifyBooking(
    bookingPublicId: string,
    payload: {
      newStartTime: string;
      newEndTime: string;
      pickupLocationPublicId?: string | null;
      returnLocationPublicId?: string | null;
      deliveryRequested?: boolean | null;
      reason?: string;
    }
  ): Observable<BookingDetailDto> {
    return this.http.patch<BookingDetailDto>(`/api/v1/tourist/bookings/${bookingPublicId}/modify`, payload);
  }

  previewCancellation(bookingPublicId: string): Observable<CancellationPreviewResponse> {
    return this.http.get<CancellationPreviewResponse>(`/api/v1/tourist/bookings/${bookingPublicId}/cancellation-preview`);
  }

  cancelBooking(bookingPublicId: string, reason?: string): Observable<BookingDetailDto> {
    return this.http.post<BookingDetailDto>(`/api/v1/tourist/bookings/${bookingPublicId}/cancel`, {
      reason: reason || 'Cancelled by tourist',
    });
  }
}
