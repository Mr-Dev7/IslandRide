import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserDto } from '../../core/models/auth.models';
import { AdminVehicleDto } from '../../core/models/vehicle.models';
import {
  AdminFinanceOverviewDto,
  BookingDetailDto,
  BookingStatus,
  BookingSummaryDto,
} from '../../core/models/booking.models';
import { AdminLayoutComponent } from '../../layouts/admin-layout/admin-layout.component';
import { AdminModerationQueueComponent } from './admin-moderation-queue.component';
import { AdminVehicleReviewComponent } from './admin-vehicle-review.component';
import { AdminBookingsComponent } from './admin-bookings.component';
import { AdminFinanceComponent } from './admin-finance.component';

function buildAdminVehicle(status: AdminVehicleDto['status'] = 'PENDING_REVIEW'): AdminVehicleDto {
  return {
    publicId: 'veh_adm1',
    businessPublicId: 'bus_1',
    businessTradeName: 'Andaman Rides',
    businessLegalName: 'Andaman Rides Pvt Ltd',
    businessVerificationStatus: 'APPROVED',
    island: { id: 1, name: 'South Andaman', displayAlias: 'Sri Vijaya Puram (Port Blair)', code: 'PORT_BLAIR', description: null, displayOrder: 1, active: true },
    pickupLocation: null,
    returnLocation: null,
    allowDifferentReturnSameIsland: false,
    category: { id: 1, name: 'Scooter / Moped', code: 'SCOOTER', slug: 'scooters', vehicleType: 'TWO_WHEELER', description: null, iconRef: null, applicableAttributes: [], active: true, displayOrder: 1 },
    title: 'Honda Activa 6G 2024',
    make: 'Honda',
    model: 'Activa 6G',
    modelYear: 2024,
    description: 'Clean scooter',
    registrationNumber: 'AN01AB1234',
    color: 'White',
    transmissionType: 'AUTOMATIC',
    fuelType: 'PETROL',
    seatingCapacity: 2,
    mileageKmpl: 45,
    engineCapacityCc: 110,
    vehicleCondition: 'EXCELLENT',
    rentalInstructions: null,
    dailyRate: 500,
    securityDeposit: 2000,
    turnaroundBufferMinutes: 60,
    bookingMode: 'INSTANT',
    status,
    rejectionReason: null,
    suspensionReason: null,
    submittedAt: '2026-11-01T10:00:00Z',
    approvedAt: null,
    moderatedBy: null,
    moderatedAt: null,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    features: [],
    images: [],
    documents: [
      {
        publicId: 'doc_rc1',
        vehiclePublicId: 'veh_adm1',
        documentTypeCode: 'RC_BOOK',
        documentTypeName: 'Registration Certificate',
        documentNumber: 'RC123',
        downloadUrl: '/api/v1/admin/vehicles/veh_adm1/documents/doc_rc1/download',
        originalFilename: 'rc.pdf',
        contentType: 'application/pdf',
        fileSizeBytes: 1024,
        issueDate: '2024-01-01',
        expiryDate: '2030-01-01',
        expired: false,
        expiringSoon: false,
        verificationStatus: 'PENDING',
        rejectionReason: null,
        verifiedAt: null,
        createdAt: '2026-11-01T10:00:00Z',
      },
    ],
    pricingOverview: null,
    statusHistory: [],
    auditLogs: [],
  };
}

describe('Admin Fleet Moderation & Role Governance', () => {
  let currentUserSignal = signal<UserDto | null>(null);

  beforeEach(() => {
    currentUserSignal = signal<UserDto | null>({
      publicId: 'usr_ops',
      email: 'ops@islandride.in',
      firstName: 'Ops',
      lastName: 'Admin',
      status: 'ACTIVE',
      roles: ['ROLE_OPERATIONS_ADMIN'],
    });

    TestBed.configureTestingModule({
      imports: [AdminLayoutComponent, AdminModerationQueueComponent, AdminVehicleReviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { currentUser: currentUserSignal, logout: jasmine.createSpy('logout') },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('AdminLayoutComponent should hide Fleet Moderation and Catalog links for SUPPORT_AGENT and FINANCE_ADMIN', () => {
    currentUserSignal.set({
      publicId: 'usr_sup',
      email: 'support@islandride.in',
      firstName: 'Support',
      lastName: 'Agent',
      status: 'ACTIVE',
      roles: ['ROLE_SUPPORT_AGENT'],
    });

    const fixture = TestBed.createComponent(AdminLayoutComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Fleet Moderation');
    expect(fixture.nativeElement.textContent).not.toContain('Catalog & Islands');

    currentUserSignal.set({
      publicId: 'usr_ops',
      email: 'ops@islandride.in',
      firstName: 'Ops',
      lastName: 'Admin',
      status: 'ACTIVE',
      roles: ['ROLE_OPERATIONS_ADMIN'],
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Fleet Moderation');
    expect(fixture.nativeElement.textContent).toContain('Catalog & Islands');
  });

  it('AdminModerationQueueComponent should render pending queue and expiring compliance alerts', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminModerationQueueComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/locations/islands').flush([]);
    httpMock.expectOne('/api/v1/categories').flush([]);
    httpMock.expectOne('/api/v1/admin/vehicles/documents/expiring?withinDays=30').flush([
      {
        publicId: 'doc_exp1',
        vehiclePublicId: 'veh_adm1',
        documentTypeCode: 'INSURANCE',
        documentTypeName: 'Motor Insurance Policy',
        documentNumber: 'POL1',
        downloadUrl: '',
        originalFilename: 'ins.pdf',
        contentType: 'application/pdf',
        fileSizeBytes: 500,
        issueDate: '2025-11-01',
        expiryDate: '2026-11-20',
        expired: false,
        expiringSoon: true,
        verificationStatus: 'VERIFIED',
        rejectionReason: null,
        verifiedAt: null,
        createdAt: '2026-11-01T10:00:00Z',
      },
    ]);
    httpMock.expectOne((r) => r.url === '/api/v1/admin/vehicles').flush({
      content: [buildAdminVehicle('PENDING_REVIEW')],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Honda Activa 6G 2024');
    expect(fixture.nativeElement.textContent).toContain('Motor Insurance Policy');
  });

  it('AdminVehicleReviewComponent should verify documents and reject listing with mandatory reason', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { paramMap: convertToParamMap({ publicId: 'veh_adm1' }) } },
    });
    const httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(AdminVehicleReviewComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/admin/vehicles/veh_adm1').flush(buildAdminVehicle('PENDING_REVIEW'));
    fixture.detectChanges();

    fixture.componentInstance.verifyDoc('doc_rc1', 'VERIFIED');
    const verReq = httpMock.expectOne('/api/v1/admin/vehicles/veh_adm1/documents/doc_rc1/verify');
    expect(verReq.request.body.status).toBe('VERIFIED');
    verReq.flush({});
    httpMock.expectOne('/api/v1/admin/vehicles/veh_adm1').flush(buildAdminVehicle('PENDING_REVIEW'));

    fixture.componentInstance.openModal('reject');
    fixture.componentInstance.modalReason = 'Insurance expired';
    fixture.componentInstance.confirmModal();
    const rejReq = httpMock.expectOne('/api/v1/admin/vehicles/veh_adm1/reject');
    expect(rejReq.request.body.reason).toBe('Insurance expired');
    rejReq.flush(buildAdminVehicle('REJECTED'));

    fixture.detectChanges();
    expect(fixture.componentInstance.vehicle()?.status).toBe('REJECTED');
  });
});

function buildAdminBooking(
  publicId = 'bkg_1',
  status: BookingStatus = 'CONFIRMED'
): BookingSummaryDto {
  return {
    publicId,
    bookingReference: 'IR-202611-TEST1',
    vehiclePublicId: 'veh_adm1',
    vehicleTitle: 'Honda Activa 6G 2024',
    businessPublicId: 'bus_1',
    partnerTradeName: 'Andaman Rides',
    customerName: 'Test Tourist',
    customerEmail: 'tourist@islandride.in',
    pickupLocationName: 'Port Blair Airport',
    returnLocationName: 'Port Blair Airport',
    startTime: '2026-12-01T10:00:00Z',
    endTime: '2026-12-03T10:00:00Z',
    totalDays: 2,
    status,
    paymentSummaryStatus: 'PAID_IN_FULL',
    overbookingExceptionStatus: 'NONE',
    bookingMode: 'INSTANT',
    paymentMode: 'FULL',
    currency: 'INR',
    totalPayableAmount: 1500,
    advancePaidAmount: 1500,
    pickupBalanceDue: 0,
    securityDepositAmount: 2000,
    depositCollectionMethod: 'ONLINE',
    approvalExpiresAt: null,
    paymentDeadlineAt: null,
    createdAt: '2026-11-01T10:00:00Z',
  };
}

function buildAdminBookingDetail(
  publicId = 'bkg_1',
  status: BookingStatus = 'CONFIRMED'
): BookingDetailDto {
  return {
    summary: buildAdminBooking(publicId, status),
    customerDlNumber: 'DL0420110012345',
    customerEmergencyContact: '+919999988888',
    dlConfirmed: true,
    dailyRate: 750,
    hourlyRate: null,
    baseRentalAmount: 1500,
    seasonalMultiplier: 1.0,
    seasonalAdjustmentAmount: 0,
    discountAmount: 0,
    surchargeAmount: 0,
    cleaningFee: 0,
    deliveryFee: 0,
    taxAmount: 75,
    gstPercentage: 5,
    commissionRateSnapshot: 4.0,
    advancePercentageApplied: 100,
    onlineAmountDueNow: 1500,
    turnaroundBufferMinutes: 60,
    pricingSnapshotJson: null,
    cancellationPolicySnapshotJson: null,
    approvedAt: '2026-11-01T10:05:00Z',
    cancelledAt: null,
    cancelledByRole: null,
    cancellationReason: null,
    disruptionExceptionType: null,
    completedAt: null,
    noShowAt: null,
    noShowEvidenceNotes: null,
    noShowEvidenceRef: null,
    securityDeposit: null,
    commission: null,
    payments: [],
    refunds: [],
    offlineCollections: [],
    inspections: [],
    statusHistory: [],
    modifications: [],
  };
}

function buildAdminFinanceOverview(): AdminFinanceOverviewDto {
  return {
    totalOnlineRentalCaptured: 15000,
    totalOfflineRentalRecorded: 5000,
    totalPlatformCommission: 1200,
    totalPendingRefundAmount: 500,
    totalCompletedRefundAmount: 2000,
    failedRefundsCount: 1,
    openOverbookingExceptionsCount: 0,
    depositTrustSummary: {
      totalExpectedDeposits: 6000,
      totalCollectedHeldLiability: 4000,
      totalInspectionHoldAmount: 1000,
      totalReleasedDeposits: 1000,
      totalForfeitedDeposits: 0,
      activeHeldCount: 2,
      inspectionHoldCount: 1,
    },
    recentPayments: [
      {
        publicId: 'pay_1',
        bookingPublicId: 'bkg_1',
        bookingReference: 'IR-202611-TEST1',
        amount: 1500,
        currency: 'INR',
        paymentType: 'RENTAL_FULL',
        deposit: false,
        paymentMethod: 'GATEWAY',
        gatewayProvider: 'RAZORPAY',
        gatewayOrderId: 'order_test_1',
        gatewayPaymentId: 'pay_test_1',
        status: 'CAPTURED',
        gatewayOrderExpiresAt: null,
        capturedAt: '2026-11-01T10:05:00Z',
        failedAt: null,
        failureReason: null,
        createdAt: '2026-11-01T10:00:00Z',
      },
    ],
    recentRefunds: [
      {
        publicId: 'ref_fail_1',
        paymentPublicId: 'pay_1',
        bookingPublicId: 'bkg_1',
        bookingReference: 'IR-202611-TEST1',
        amount: 500,
        gatewayFeeRetained: 0,
        currency: 'INR',
        reason: 'Temporary gateway downtime',
        refundType: 'PARTIAL_ADJUSTMENT',
        gatewayRefundId: null,
        status: 'FAILED',
        retryCount: 1,
        nextRetryAt: '2026-11-01T12:00:00Z',
        errorMessage: 'Network timeout to payment gateway',
        escalationTicketRef: null,
        reconciledAt: null,
        createdAt: '2026-11-01T10:30:00Z',
      },
    ],
    recentWebhooks: [],
    overbookingExceptions: [],
  };
}

describe('AdminBookingsComponent Operations & Disruption Governance', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminBookingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('should render bookings list and overbooking exceptions tab', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminBookingsComponent);
    fixture.detectChanges();

    const bookingsReq = httpMock.expectOne((r) => r.url === '/api/v1/admin/bookings');
    expect(bookingsReq.request.params.get('page')).toBe('0');
    expect(bookingsReq.request.params.get('size')).toBe('50');
    bookingsReq.flush({
      content: [buildAdminBooking('bkg_1', 'CONFIRMED')],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    const exceptionsReq = httpMock.expectOne('/api/v1/admin/overbooking-exceptions');
    exceptionsReq.flush([
      {
        publicId: 'obx_1',
        bookingPublicId: 'bkg_1',
        bookingReference: 'IR-202611-TEST1',
        conflictingBookingPublicId: null,
        conflictingBookingReference: null,
        paymentId: 1,
        status: 'OPEN',
        resolutionNotes: null,
        detectedAt: '2026-11-01T10:00:00Z',
        resolvedAt: null,
      },
    ]);

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('IR-202611-TEST1');
    expect(fixture.nativeElement.textContent).toContain('Honda Activa 6G 2024');
    expect(fixture.nativeElement.textContent).toContain('CONFIRMED');

    fixture.componentInstance.activeTab.set('exceptions');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('obx_1');
  });

  it('should execute disruption override on confirmed booking with full protection', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminBookingsComponent);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/api/v1/admin/bookings').flush({
      content: [buildAdminBooking('bkg_1', 'CONFIRMED')],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });
    httpMock.expectOne('/api/v1/admin/overbooking-exceptions').flush([]);
    fixture.detectChanges();

    fixture.componentInstance.openDisruptionModal(buildAdminBooking('bkg_1', 'CONFIRMED'));
    expect(fixture.componentInstance.showDisruptionModal()).toBeTrue();

    fixture.componentInstance.disruptionForm.type = 'WEATHER_DISRUPTION';
    fixture.componentInstance.disruptionForm.reason = 'Cyclone Alert for Swaraj Dweep';
    fixture.componentInstance.disruptionForm.preferReschedule = false;

    fixture.componentInstance.executeDisruptionOverride();
    const disrReq = httpMock.expectOne('/api/v1/admin/bookings/bkg_1/disruption-override');
    expect(disrReq.request.body).toEqual({
      disruptionType: 'WEATHER_DISRUPTION',
      reason: 'Cyclone Alert for Swaraj Dweep',
      preferReschedule: false,
    });
    disrReq.flush(buildAdminBookingDetail('bkg_1', 'CANCELLED'));

    // Should refresh bookings list
    httpMock.expectOne((r) => r.url === '/api/v1/admin/bookings').flush({
      content: [buildAdminBooking('bkg_1', 'CANCELLED')],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    expect(fixture.componentInstance.showDisruptionModal()).toBeFalse();
    expect(fixture.componentInstance.alert()?.message).toContain('Disruption override applied successfully');
  });

  it('should execute expiration sweep for stale bookings', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminBookingsComponent);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/api/v1/admin/bookings').flush({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    httpMock.expectOne('/api/v1/admin/overbooking-exceptions').flush([]);
    fixture.detectChanges();

    fixture.componentInstance.triggerExpirationSweep();
    const sweepReq = httpMock.expectOne('/api/v1/admin/bookings/expire-sweep');
    sweepReq.flush({ status: 'SUCCESS', expiredBookingsCount: 3 });

    httpMock.expectOne((r) => r.url === '/api/v1/admin/bookings').flush({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    expect(fixture.componentInstance.alert()?.message).toContain('Expired 3 bookings');
  });
});

describe('AdminFinanceComponent Trust Ledger & Refund Governance', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminFinanceComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('should render financial overview, deposit trust liabilities, and recent refunds', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminFinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('15,000.00');
    expect(fixture.nativeElement.textContent).toContain('4,000.00');
    expect(fixture.nativeElement.textContent).toContain('ref_fail_1');
    expect(fixture.nativeElement.textContent).toContain('Network timeout to payment gateway');
  });

  it('should trigger stale payment reconciliation and reload overview', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminFinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    fixture.componentInstance.triggerReconciliation();
    const recReq = httpMock.expectOne('/api/v1/admin/finance/reconcile-payments');
    recReq.flush({ status: 'SUCCESS', reconciledPaymentsCount: 2 });

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    expect(fixture.componentInstance.alert()?.message).toContain('Reconciled 2 stale payments');
  });

  it('should open manual refund modal and issue administrative refund', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminFinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    fixture.componentInstance.openManualRefundModal();
    expect(fixture.componentInstance.showManualRefundModal()).toBeTrue();

    fixture.componentInstance.manualRefundForm.bookingPublicId = 'bkg_1';
    fixture.componentInstance.manualRefundForm.amount = 500;
    fixture.componentInstance.manualRefundForm.reason = 'Administrative concession';
    fixture.componentInstance.manualRefundForm.type = 'MANUAL';

    fixture.componentInstance.executeManualRefund();
    const refReq = httpMock.expectOne('/api/v1/admin/finance/refunds/manual/bkg_1');
    expect(refReq.request.body).toEqual({
      amount: 500,
      reason: 'Administrative concession',
      refundType: 'MANUAL',
    });
    expect(refReq.request.headers.has('Idempotency-Key')).toBeTrue();
    refReq.flush({
      publicId: 'ref_man_1',
      paymentPublicId: 'pay_1',
      bookingPublicId: 'bkg_1',
      bookingReference: 'IR-202611-TEST1',
      amount: 500,
      gatewayFeeRetained: 0,
      currency: 'INR',
      reason: 'Administrative concession',
      refundType: 'MANUAL',
      gatewayRefundId: 'rfnd_gw_1',
      status: 'SUCCESS',
      retryCount: 0,
      nextRetryAt: null,
      errorMessage: null,
      escalationTicketRef: null,
      reconciledAt: null,
      createdAt: '2026-11-01T11:00:00Z',
    });

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    expect(fixture.componentInstance.showManualRefundModal()).toBeFalse();
    expect(fixture.componentInstance.alert()?.message).toContain('Manual refund recorded successfully');
  });

  it('should retry failed refund', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminFinanceComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    fixture.componentInstance.retryRefund('ref_fail_1');
    const retryReq = httpMock.expectOne('/api/v1/admin/finance/refunds/ref_fail_1/retry?forceMarkReconciled=false');
    retryReq.flush({
      publicId: 'ref_fail_1',
      paymentPublicId: 'pay_1',
      bookingPublicId: 'bkg_1',
      bookingReference: 'IR-202611-TEST1',
      amount: 500,
      gatewayFeeRetained: 0,
      currency: 'INR',
      reason: 'Temporary gateway downtime',
      refundType: 'PARTIAL_ADJUSTMENT',
      gatewayRefundId: 'rfnd_gw_retry',
      status: 'SUCCESS',
      retryCount: 2,
      nextRetryAt: null,
      errorMessage: null,
      escalationTicketRef: null,
      reconciledAt: null,
      createdAt: '2026-11-01T10:30:00Z',
    });

    httpMock.expectOne('/api/v1/admin/finance/overview').flush(buildAdminFinanceOverview());
    fixture.detectChanges();

    expect(fixture.componentInstance.alert()?.message).toContain('Refund retry initiated for ref_fail_1');
  });
});

