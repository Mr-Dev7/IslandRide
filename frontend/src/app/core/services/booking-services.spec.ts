import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BookingService } from './booking.service';
import { PartnerBookingService } from './partner-booking.service';
import { AdminBookingFinanceService } from './admin-booking-finance.service';

describe('Phase 6B Booking, Partner & Admin Finance Services', () => {
  let httpMock: HttpTestingController;
  let bookingService: BookingService;
  let partnerBookingService: PartnerBookingService;
  let adminService: AdminBookingFinanceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    bookingService = TestBed.inject(BookingService);
    partnerBookingService = TestBed.inject(PartnerBookingService);
    adminService = TestBed.inject(AdminBookingFinanceService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('BookingService should calculate quotes, acquire holds, checkout, and cancel bookings', () => {
    bookingService.getPublicQuote(
      'veh_test123',
      '2026-12-01T10:00:00Z',
      '2026-12-03T10:00:00Z',
      { paymentMode: 'FULL' }
    ).subscribe();

    const quoteReq = httpMock.expectOne((r) => r.url === '/api/v1/public/bookings/quote/veh_test123');
    expect(quoteReq.request.method).toBe('GET');
    expect(quoteReq.request.params.get('startTime')).toBe('2026-12-01T10:00:00Z');
    quoteReq.flush({});

    bookingService.acquireHold({
      vehiclePublicId: 'veh_test123',
      startTime: '2026-12-01T10:00:00Z',
      endTime: '2026-12-03T10:00:00Z',
    }, 'idemp_hold_1').subscribe();

    const holdReq = httpMock.expectOne('/api/v1/tourist/holds');
    expect(holdReq.request.method).toBe('POST');
    expect(holdReq.request.headers.get('Idempotency-Key')).toBe('idemp_hold_1');
    holdReq.flush({ publicId: 'hld_1' });

    bookingService.createBooking({
      holdPublicId: 'hld_1',
      paymentMode: 'FULL',
      deliveryRequested: false,
      customerDlNumber: 'DL12345',
      customerEmergencyContact: '+919876543210',
      dlConfirmed: true,
      idempotencyKey: 'idemp_bkg_1',
    }, 'idemp_bkg_1').subscribe();

    const bkgReq = httpMock.expectOne('/api/v1/tourist/bookings');
    expect(bkgReq.request.method).toBe('POST');
    expect(bkgReq.request.headers.get('Idempotency-Key')).toBe('idemp_bkg_1');
    bkgReq.flush({});

    bookingService.previewCancellation('bkg_1').subscribe();
    const prevReq = httpMock.expectOne('/api/v1/tourist/bookings/bkg_1/cancellation-preview');
    expect(prevReq.request.method).toBe('GET');
    prevReq.flush({});

    bookingService.cancelBooking('bkg_1', 'Changed plans').subscribe();
    const cxlReq = httpMock.expectOne('/api/v1/tourist/bookings/bkg_1/cancel');
    expect(cxlReq.request.method).toBe('POST');
    cxlReq.flush({});
  });

  it('PartnerBookingService should approve, reject, handover pickup, and complete return', () => {
    partnerBookingService.listBookings('biz_1', 0, 10).subscribe();
    const listReq = httpMock.expectOne((r) => r.url === '/api/v1/partners/bookings');
    expect(listReq.request.params.get('businessPublicId')).toBe('biz_1');
    listReq.flush({ content: [] });

    partnerBookingService.approveBooking('bkg_2', 'Approved').subscribe();
    const appReq = httpMock.expectOne('/api/v1/partners/bookings/bkg_2/approve');
    expect(appReq.request.method).toBe('POST');
    appReq.flush({});

    partnerBookingService.rejectBooking('bkg_2', 'No vehicle').subscribe();
    const rejReq = httpMock.expectOne('/api/v1/partners/bookings/bkg_2/reject');
    expect(rejReq.request.method).toBe('POST');
    rejReq.flush({});

    partnerBookingService.recordPickupHandover('bkg_2', {
      odometerReading: 12000,
      fuelPercentage: 95,
      checklistDataJson: '{}',
      notes: 'Clean vehicle',
      depositWaived: false,
      evidence: [],
    }).subscribe();
    const pkReq = httpMock.expectOne('/api/v1/partners/bookings/bkg_2/handover/pickup');
    expect(pkReq.request.method).toBe('POST');
    pkReq.flush({});

    partnerBookingService.recordReturnCompletion('bkg_2', {
      odometerReading: 12150,
      fuelPercentage: 90,
      checklistDataJson: '{}',
      notes: 'Returned fine',
      damageReported: false,
      releaseDepositImmediately: true,
      evidence: [],
    }).subscribe();
    const retReq = httpMock.expectOne('/api/v1/partners/bookings/bkg_2/handover/return');
    expect(retReq.request.method).toBe('POST');
    retReq.flush({});
  });

  it('AdminBookingFinanceService should fetch finance overview, issue manual refund, and trigger reconciliations', () => {
    adminService.getFinanceOverview().subscribe();
    httpMock.expectOne('/api/v1/admin/finance/overview').flush({});

    adminService.issueManualRefund('bkg_3', {
      amount: 1500,
      reason: 'Goodwill refund',
      refundType: 'MANUAL',
    }, 'idemp_ref_1').subscribe();
    const refReq = httpMock.expectOne('/api/v1/admin/finance/refunds/manual/bkg_3');
    expect(refReq.request.method).toBe('POST');
    expect(refReq.request.headers.get('Idempotency-Key')).toBe('idemp_ref_1');
    refReq.flush({});

    adminService.triggerPaymentReconciliation().subscribe();
    const recReq = httpMock.expectOne('/api/v1/admin/finance/reconcile-payments');
    expect(recReq.request.method).toBe('POST');
    recReq.flush({ status: 'SUCCESS', reconciledPaymentsCount: 2 });

    adminService.triggerBookingExpirationSweep().subscribe();
    const expReq = httpMock.expectOne('/api/v1/admin/bookings/expire-sweep');
    expect(expReq.request.method).toBe('POST');
    expReq.flush({ status: 'SUCCESS', expiredBookingsCount: 5 });

    adminService.applyDisruptionOverride('bkg_3', {
      disruptionType: 'WEATHER_DISRUPTION',
      reason: 'Cyclone warning',
      preferReschedule: false,
    }).subscribe();
    const disrReq = httpMock.expectOne('/api/v1/admin/bookings/bkg_3/disruption-override');
    expect(disrReq.request.method).toBe('POST');
    disrReq.flush({});
  });
});
