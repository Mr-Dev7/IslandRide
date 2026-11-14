import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError, Subject } from 'rxjs';

import { TouristBookingDetailComponent } from './tourist-booking-detail.component';
import { BookingService } from '../../../core/services/booking.service';
import { AuthService } from '../../../core/services/auth.service';
import { RazorpayLoaderService } from '../../../core/services/razorpay-loader.service';
import {
  BookingDetailDto,
  GatewayCheckoutOrderResponse,
  MockPaymentSimulateResponse,
  PaymentVerificationResponse,
} from '../../../core/models/booking.models';
import {
  RazorpayConstructor,
  RazorpayErrorResponse,
  RazorpayInstance,
  RazorpayOptions,
  RazorpayPaymentSuccessResponse,
} from '../../../core/models/razorpay.models';

class MockRazorpayInstance implements RazorpayInstance {
  static lastInstance: MockRazorpayInstance | null = null;
  static lastOptions: RazorpayOptions | null = null;

  events: Record<string, (arg?: any) => void> = {};
  openCalled = false;
  closeCalled = false;

  constructor(public options: RazorpayOptions) {
    MockRazorpayInstance.lastInstance = this;
    MockRazorpayInstance.lastOptions = options;
  }

  open(): void {
    this.openCalled = true;
  }

  close(): void {
    this.closeCalled = true;
  }

  on(event: string, handler: (response: any) => void): void {
    this.events[event] = handler;
  }
}

describe('TouristBookingDetailComponent — Razorpay Payment Integration', () => {
  let component: TouristBookingDetailComponent;
  let fixture: ComponentFixture<TouristBookingDetailComponent>;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;
  let razorpayLoaderSpy: jasmine.SpyObj<RazorpayLoaderService>;

  const createMockBooking = (status: string, deadlineOffsetHours = 2): BookingDetailDto => {
    const deadline = new Date(Date.now() + deadlineOffsetHours * 3600 * 1000).toISOString();
    return {
      summary: {
        publicId: 'bkg_pub_test_999',
        bookingReference: 'IR-2026-BKG999',
        vehiclePublicId: 'veh_test_1',
        vehicleTitle: 'Royal Enfield Classic 350',
        businessPublicId: 'bus_1',
        partnerTradeName: 'Island Riders Havelock',
        customerName: 'John Doe',
        customerEmail: 'tourist@islandride.in',
        pickupLocationName: 'Havelock Jetty',
        returnLocationName: 'Havelock Jetty',
        startTime: '2026-12-01T10:00:00Z',
        endTime: '2026-12-03T10:00:00Z',
        totalDays: 2,
        bookingMode: 'INSTANT',
        paymentMode: 'FULL',
        status: status as any,
        paymentSummaryStatus: status === 'CONFIRMED' ? 'PAID_IN_FULL' : 'ORDER_CREATED',
        overbookingExceptionStatus: 'NONE',
        currency: 'INR',
        totalPayableAmount: 2360,
        advancePaidAmount: status === 'CONFIRMED' ? 2360 : 0,
        pickupBalanceDue: 0,
        securityDepositAmount: 3000,
        depositCollectionMethod: 'AT_PICKUP',
        approvalExpiresAt: null,
        paymentDeadlineAt: deadline,
        createdAt: new Date().toISOString(),
      },
      customerDlNumber: 'DL-AN01-2023-009988',
      customerEmergencyContact: '+919876543210',
      dlConfirmed: true,
      dailyRate: 1000,
      hourlyRate: null,
      baseRentalAmount: 2000,
      seasonalMultiplier: 1.0,
      seasonalAdjustmentAmount: 0,
      discountAmount: 0,
      surchargeAmount: 0,
      cleaningFee: 0,
      deliveryFee: 0,
      taxAmount: 360,
      gstPercentage: 18,
      commissionRateSnapshot: 4.0,
      advancePercentageApplied: 100,
      onlineAmountDueNow: 2360,
      turnaroundBufferMinutes: 60,
      pricingSnapshotJson: null,
      cancellationPolicySnapshotJson: null,
      approvedAt: null,
      cancelledAt: null,
      cancelledByRole: null,
      cancellationReason: null,
      disruptionExceptionType: null,
      completedAt: null,
      noShowAt: null,
      noShowEvidenceNotes: null,
      noShowEvidenceRef: null,
      securityDeposit: {
        publicId: 'dep_1',
        collectionMethod: 'AT_PICKUP',
        expectedAmount: 3000,
        collectedAmount: 0,
        refundedAmount: 0,
        forfeitedAmount: 0,
        currency: 'INR',
        status: 'PENDING',
        inspectionStatus: 'PENDING',
        inspectionDeadlineAt: null,
        paymentChannel: null,
        receiptReference: null,
        collectedAt: null,
        releasedAt: null,
        notes: null,
      },
      commission: null,
      payments: [],
      refunds: [],
      offlineCollections: [],
      inspections: [],
      statusHistory: [],
      modifications: [],
    };
  };

  const mockRazorpayCheckoutOrder: GatewayCheckoutOrderResponse = {
    paymentPublicId: 'pay_pub_456',
    bookingPublicId: 'bkg_pub_test_999',
    bookingReference: 'IR-2026-BKG999',
    gatewayProvider: 'razorpay',
    gatewayOrderId: 'order_rzp_mock_detail_456',
    keyId: 'rzp_test_public_key_abc',
    amount: 2360,
    currency: 'INR',
    paymentType: 'RENTAL_FULL',
    liveEnabled: false,
    orderExpiresAt: new Date(Date.now() + 1800000).toISOString(),
    bookingPaymentDeadlineAt: new Date(Date.now() + 1800000).toISOString(),
  };

  const mockUser = {
    id: 101,
    email: 'tourist@islandride.in',
    firstName: 'John',
    lastName: 'Doe',
    phone: '+919876543210',
    roles: ['ROLE_TOURIST'],
  };

  beforeEach(async () => {
    MockRazorpayInstance.lastInstance = null;
    MockRazorpayInstance.lastOptions = null;

    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', [
      'getMyBooking',
      'initiateCheckoutOrder',
      'verifyPayment',
      'simulateMockPayment',
      'modifyBooking',
      'previewCancellation',
      'cancelBooking',
    ]);

    razorpayLoaderSpy = jasmine.createSpyObj<RazorpayLoaderService>('RazorpayLoaderService', ['load']);

    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('PENDING_PAYMENT')));

    const mockAuthService = {
      currentUser: signal(mockUser),
    };

    await TestBed.configureTestingModule({
      imports: [TouristBookingDetailComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'publicId' ? 'bkg_pub_test_999' : null),
              },
            },
          },
        },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: AuthService, useValue: mockAuthService },
        { provide: RazorpayLoaderService, useValue: razorpayLoaderSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TouristBookingDetailComponent);
    component = fixture.componentInstance;
  });

  it('should initialize and render booking details with payment action banner for PENDING_PAYMENT', () => {
    fixture.detectChanges();

    expect(component.booking()).not.toBeNull();
    expect(component.booking()?.summary.status).toBe('PENDING_PAYMENT');
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBeNull();

    const bannerText = fixture.nativeElement.querySelector('.action-banner strong')?.textContent;
    expect(bannerText).toContain('Payment Required to Confirm Vehicle');

    const payButton = fixture.nativeElement.querySelector('.action-banner button');
    expect(payButton?.textContent.trim()).toBe('Pay Online Now');
  });

  it('should render payment action banner for APPROVED bookings', () => {
    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('APPROVED')));
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.action-banner');
    expect(banner).not.toBeNull();
    const payButton = banner.querySelector('button');
    expect(payButton?.textContent.trim()).toBe('Pay Online Now');
  });

  it('should render danger action banner with retry text for PAYMENT_FAILED bookings', () => {
    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('PAYMENT_FAILED')));
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('.action-banner.danger');
    expect(banner).not.toBeNull();
    expect(banner.textContent).toContain('Payment Failed · Action Required');

    const payButton = banner.querySelector('button');
    expect(payButton?.textContent.trim()).toBe('Retry Payment Online');
  });

  it('should NOT render payment action banner for CONFIRMED, ACTIVE, or CANCELLED bookings', () => {
    const statuses = ['CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED'];
    for (const st of statuses) {
      bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking(st)));
      const testFixture = TestBed.createComponent(TouristBookingDetailComponent);
      testFixture.detectChanges();

      const banner = testFixture.nativeElement.querySelector('.action-banner');
      expect(banner).toBeNull();
    }
  });

  it('should disable pay button when payment deadline has expired', () => {
    // 2 hours in the past
    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('PENDING_PAYMENT', -2)));
    fixture.detectChanges();

    const b = component.booking()!;
    expect(component.isPaymentDeadlineExpired(b)).toBeTrue();

    const payButton = fixture.nativeElement.querySelector('.action-banner button');
    expect(payButton.disabled).toBeTrue();
  });

  it('should initiate checkout and launch Razorpay when order gatewayProvider is razorpay', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledWith(
      'bkg_pub_test_999',
      jasmine.stringMatching(/^retry_pay_bkg_pub_test_999_\d+$/)
    );

    expect(razorpayLoaderSpy.load).toHaveBeenCalled();
    expect(MockRazorpayInstance.lastInstance).not.toBeNull();
    expect(MockRazorpayInstance.lastInstance!.openCalled).toBeTrue();

    const options = MockRazorpayInstance.lastOptions!;
    expect(options.key).toBe('rzp_test_public_key_abc');
    expect(options.amount).toBe(236000); // 2360 INR * 100 paise
    expect(options.currency).toBe('INR');
    expect(options.order_id).toBe('order_rzp_mock_detail_456');
    expect(options.name).toBe('IslandRide');
    expect(options.prefill?.name).toBe('John Doe');
    expect(options.prefill?.email).toBe('tourist@islandride.in');
    expect(options.prefill?.contact).toBe('+919876543210');
    expect(options.theme?.color).toBe('#0f766e');
  }));

  it('should abort and show error when order.keyId is missing or empty', fakeAsync(() => {
    fixture.detectChanges();

    const orderWithoutKeyId: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      keyId: null,
    };
    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(orderWithoutKeyId));

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    expect(razorpayLoaderSpy.load).not.toHaveBeenCalled();
    expect(component.paymentError()).toBe(
      'Payment gateway configuration error: public Key ID is missing.'
    );
    expect(component.submitting()).toBeFalse();
  }));

  it('should handle Razorpay SDK loading failure gracefully', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(Promise.reject(new Error('Network timeout loading SDK')));

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    expect(component.paymentError()).toContain('Network timeout loading SDK');
    expect(component.submitting()).toBeFalse();
  }));

  it('should verify payment on successful razorpay callback and refresh booking details', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const verificationResponse: PaymentVerificationResponse = {
      bookingPublicId: 'bkg_pub_test_999',
      bookingReference: 'IR-2026-BKG999',
      bookingStatus: 'CONFIRMED',
      paymentSummaryStatus: 'PAID_IN_FULL',
      overbookingDetected: false,
      payment: {} as any,
    };
    bookingServiceSpy.verifyPayment.and.returnValue(of(verificationResponse));

    const confirmedBooking = createMockBooking('CONFIRMED');
    bookingServiceSpy.getMyBooking.and.returnValue(of(confirmedBooking));

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    // Trigger success callback
    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_detail_test_777',
      razorpay_order_id: 'order_rzp_mock_detail_456',
      razorpay_signature: 'test_crypto_signature_hex_detail_777',
    };
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    tick();

    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledWith(
      {
        bookingPublicId: 'bkg_pub_test_999',
        gatewayOrderId: 'order_rzp_mock_detail_456',
        gatewayPaymentId: 'pay_detail_test_777',
        gatewaySignature: 'test_crypto_signature_hex_detail_777',
      },
      'ver_order_rzp_mock_detail_456'
    );

    // Assert booking was re-fetched and updated to CONFIRMED
    expect(component.booking()?.summary.status).toBe('CONFIRMED');
    expect(component.submitting()).toBeFalse();
    expect(component.paymentError()).toBeNull();
  }));

  it('should handle backend verification failure without refreshing and display actionable message', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    bookingServiceSpy.verifyPayment.and.returnValue(
      throwError(() => ({ error: { detail: 'Invalid payment signature.' } }))
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_tampered_777',
      razorpay_order_id: 'order_rzp_mock_detail_456',
      razorpay_signature: 'invalid_signature_hex',
    };
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    tick();

    expect(component.paymentError()).toContain('Invalid payment signature');
    expect(component.paymentError()).toContain('webhook reconciliation');
    expect(component.submitting()).toBeFalse();
    // Booking remains PENDING_PAYMENT
    expect(component.booking()?.summary.status).toBe('PENDING_PAYMENT');
  }));

  it('should handle payment.failed event from Razorpay instance and keep modal active', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    const failedPayload: RazorpayErrorResponse = {
      error: {
        code: 'BAD_REQUEST_ERROR',
        description: 'Insufficient funds in customer account',
        source: 'bank',
        step: 'payment_authorization',
        reason: 'payment_failed',
      },
    };

    const failHandler = MockRazorpayInstance.lastInstance!.events['payment.failed'];
    expect(failHandler).toBeDefined();
    failHandler(failedPayload);

    expect(component.paymentError()).toContain('Insufficient funds in customer account');
    expect(component.paymentError()).toContain('checkout window or close it to retry');
    expect(component.paymentStatus()).toContain('still active');
    expect(component.submitting()).toBeTrue();
    expect(component.razorpayModalActive()).toBeTrue();
  }));

  it('should prevent opening overlapping checkout instances while previous modal is active', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    expect(component.razorpayModalActive()).toBeTrue();
    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledTimes(1);

    // Call openPaymentModal while modal is active - should be ignored
    component.openPaymentModal(bookingData);
    tick();
    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledTimes(1);

    // Payment fails - modal still active
    const failedPayload: RazorpayErrorResponse = {
      error: {
        code: 'BAD_REQUEST_ERROR',
        description: 'Card declined',
        source: 'bank',
        step: 'payment_authorization',
        reason: 'payment_failed',
      },
    };
    MockRazorpayInstance.lastInstance!.events['payment.failed'](failedPayload);

    // Call openPaymentModal after failure while modal is still open - should still be blocked
    component.openPaymentModal(bookingData);
    tick();
    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledTimes(1);

    // Now user closes/dismisses the modal
    MockRazorpayInstance.lastOptions!.modal!.ondismiss!();
    expect(component.razorpayModalActive()).toBeFalse();
    expect(component.submitting()).toBeFalse();

    // Now safe retry is allowed and opens a new checkout order
    component.openPaymentModal(bookingData);
    tick();
    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledTimes(2);
  }));

  it('should guard against repeated success callbacks and concurrent verification requests for the same payment order', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const verifySubject = new Subject<PaymentVerificationResponse>();
    bookingServiceSpy.verifyPayment.and.returnValue(verifySubject.asObservable());

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_detail_test_777',
      razorpay_order_id: 'order_rzp_mock_detail_456',
      razorpay_signature: 'test_crypto_signature_hex_detail_777',
    };

    // First success callback fires verification
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledTimes(1);

    // Repeated success callback for the same order while in-flight is safely ignored
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledTimes(1);

    // Resolve verification
    const verificationResponse: PaymentVerificationResponse = {
      bookingPublicId: 'bkg_pub_test_999',
      bookingReference: 'IR-2026-BKG999',
      bookingStatus: 'CONFIRMED',
      paymentSummaryStatus: 'PAID_IN_FULL',
      overbookingDetected: false,
      payment: {} as any,
    };
    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('CONFIRMED')));
    verifySubject.next(verificationResponse);
    verifySubject.complete();
    tick();

    expect(component.booking()?.summary.status).toBe('CONFIRMED');
    expect(component.submitting()).toBeFalse();
  }));

  it('should allow a legitimate retry after a failed verification attempt', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    // First attempt fails during verification
    bookingServiceSpy.verifyPayment.and.returnValue(
      throwError(() => ({ error: { detail: 'Temporary verification server error' } }))
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_detail_test_777',
      razorpay_order_id: 'order_rzp_mock_detail_456',
      razorpay_signature: 'test_crypto_signature_hex_detail_777',
    };
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    tick();

    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledTimes(1);
    expect(component.paymentError()).toContain('Temporary verification server error');
    expect(component.submitting()).toBeFalse();
    expect(component.razorpayModalActive()).toBeFalse();

    // Legitimate retry: second payment attempt initiated and verified
    const secondOrder: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      gatewayOrderId: 'order_rzp_mock_detail_retry_888',
    };
    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(secondOrder));

    const verificationResponse: PaymentVerificationResponse = {
      bookingPublicId: 'bkg_pub_test_999',
      bookingReference: 'IR-2026-BKG999',
      bookingStatus: 'CONFIRMED',
      paymentSummaryStatus: 'PAID_IN_FULL',
      overbookingDetected: false,
      payment: {} as any,
    };
    bookingServiceSpy.verifyPayment.and.returnValue(of(verificationResponse));
    bookingServiceSpy.getMyBooking.and.returnValue(of(createMockBooking('CONFIRMED')));

    component.openPaymentModal(bookingData);
    tick();

    const secondSuccessPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_detail_test_888',
      razorpay_order_id: 'order_rzp_mock_detail_retry_888',
      razorpay_signature: 'sig_retry_888',
    };
    MockRazorpayInstance.lastOptions!.handler!(secondSuccessPayload);
    tick();

    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledTimes(2);
    expect(component.booking()?.summary.status).toBe('CONFIRMED');
    expect(component.paymentError()).toBeNull();
  }));

  it('should handle modal.ondismiss by showing a retry message', fakeAsync(() => {
    fixture.detectChanges();

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    MockRazorpayInstance.lastOptions!.modal!.ondismiss!();

    expect(component.submitting()).toBeFalse();
    expect(component.paymentStatus()).toBeNull();
    expect(component.paymentError()).toBe(
      'Payment was not completed. You can retry before your payment window expires.'
    );
  }));

  it('should prevent duplicate payment submissions while in-flight', () => {
    fixture.detectChanges();

    component.submitting.set(true);

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);

    expect(bookingServiceSpy.initiateCheckoutOrder).not.toHaveBeenCalled();
  });

  it('should open mock gateway simulator modal when order gatewayProvider is mock', fakeAsync(() => {
    fixture.detectChanges();

    const mockGatewayOrder: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      gatewayProvider: 'mock',
      keyId: null,
    };
    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockGatewayOrder));

    const bookingData = component.booking()!;
    component.openPaymentModal(bookingData);
    tick();

    expect(razorpayLoaderSpy.load).not.toHaveBeenCalled();
    expect(component.activeOrder()).toEqual(mockGatewayOrder);
    expect(component.submitting()).toBeFalse();
  }));

  it('should complete mock payment and refresh booking details', fakeAsync(() => {
    fixture.detectChanges();

    const mockGatewayOrder: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      gatewayProvider: 'mock',
      keyId: null,
    };
    component.activeOrder.set(mockGatewayOrder);

    const mockSimResponse: MockPaymentSimulateResponse = {
      gatewayOrderId: 'order_rzp_mock_detail_456',
      gatewayPaymentId: 'pay_mock_sim_999',
      checkoutSignature: 'mock_sig_hex_999',
      webhookPayload: '{}',
      webhookSignature: 'wh_sig',
      webhookEventId: 'evt_1',
      outcome: 'CAPTURE_SUCCESS',
    };
    bookingServiceSpy.simulateMockPayment.and.returnValue(of(mockSimResponse));

    const verificationResponse: PaymentVerificationResponse = {
      bookingPublicId: 'bkg_pub_test_999',
      bookingReference: 'IR-2026-BKG999',
      bookingStatus: 'CONFIRMED',
      paymentSummaryStatus: 'PAID_IN_FULL',
      overbookingDetected: false,
      payment: {} as any,
    };
    bookingServiceSpy.verifyPayment.and.returnValue(of(verificationResponse));

    const confirmedBooking = createMockBooking('CONFIRMED');
    bookingServiceSpy.getMyBooking.and.returnValue(of(confirmedBooking));

    const bookingData = component.booking()!;
    component.completeMockPayment(bookingData, mockGatewayOrder);
    tick();

    expect(bookingServiceSpy.simulateMockPayment).toHaveBeenCalledWith(
      'order_rzp_mock_detail_456',
      'CAPTURE_SUCCESS'
    );
    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledWith(
      {
        bookingPublicId: 'bkg_pub_test_999',
        gatewayOrderId: 'order_rzp_mock_detail_456',
        gatewayPaymentId: 'pay_mock_sim_999',
        gatewaySignature: 'mock_sig_hex_999',
      },
      'ver_order_rzp_mock_detail_456'
    );
    expect(component.activeOrder()).toBeNull();
    expect(component.booking()?.summary.status).toBe('CONFIRMED');
  }));

  it('should handle missing booking public identifier in route snapshot', () => {
    const route = TestBed.inject(ActivatedRoute);
    spyOn(route.snapshot.paramMap, 'get').and.returnValue(null);

    const testFixture = TestBed.createComponent(TouristBookingDetailComponent);
    testFixture.detectChanges();

    expect(testFixture.componentInstance.error()).toBe('Missing booking public identifier.');
    expect(testFixture.componentInstance.loading()).toBeFalse();
  });
});
