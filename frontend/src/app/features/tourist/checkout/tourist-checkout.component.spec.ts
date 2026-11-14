import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { TouristCheckoutComponent } from './tourist-checkout.component';
import { BookingService } from '../../../core/services/booking.service';
import { AuthService } from '../../../core/services/auth.service';
import { RazorpayLoaderService } from '../../../core/services/razorpay-loader.service';
import {
  BookingCreationResult,
  BookingDetailDto,
  GatewayCheckoutOrderResponse,
  InventoryHoldResponse,
  PaymentVerificationResponse,
  PricePreviewResponse,
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

describe('TouristCheckoutComponent — Razorpay Checkout & Verification', () => {
  let component: TouristCheckoutComponent;
  let fixture: ComponentFixture<TouristCheckoutComponent>;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;
  let razorpayLoaderSpy: jasmine.SpyObj<RazorpayLoaderService>;
  let router: Router;

  const mockQuote: PricePreviewResponse = {
    vehiclePublicId: 'veh_test_1',
    vehicleTitle: 'Royal Enfield Classic 350',
    bookingMode: 'INSTANT',
    available: true,
    conflictReason: null,
    startTime: '2026-12-01T10:00:00Z',
    endTime: '2026-12-03T10:00:00Z',
    pickupLocationPublicId: null,
    pickupLocationName: 'Havelock Jetty',
    returnLocationPublicId: null,
    returnLocationName: 'Havelock Jetty',
    currency: 'INR',
    billingUnit: 'DAY',
    billableDays: 2,
    billableHours: null,
    dailyRate: 1000,
    hourlyRate: null,
    baseRentalAmount: 2000,
    seasonalMultiplier: 1.0,
    seasonalAdjustmentAmount: 0,
    discountAmount: 0,
    surchargeAmount: 0,
    cleaningFee: 0,
    deliveryFee: 0,
    gstPercentage: 18,
    taxAmount: 360,
    totalPayableAmount: 2360,
    securityDepositAmount: 3000,
    depositCollectionMethod: 'AT_PICKUP',
    requestedPaymentMode: 'FULL',
    enforcedPaymentMode: 'FULL',
    advancePercentageApplied: 100,
    advanceDueNow: 2360,
    pickupBalanceDue: 0,
    turnaroundBufferMinutes: 60,
    fullPaymentCutoffHours: 24,
    holdDurationSeconds: 600,
    cancellationDeadlineHours: 24,
    freeCancellationAllowed: true,
    cancellationFeePercentage: 0,
  };

  const mockHold: InventoryHoldResponse = {
    publicId: 'hld_test123',
    vehiclePublicId: 'veh_test_1',
    vehicleTitle: 'Royal Enfield Classic 350',
    partnerTradeName: 'Island Riders Havelock',
    islandName: 'Havelock Island (Swaraj Dweep)',
    bookingMode: 'INSTANT',
    purpose: 'CHECKOUT',
    status: 'HELD',
    pickupLocationPublicId: null,
    pickupLocationName: 'Havelock Jetty',
    returnLocationPublicId: null,
    returnLocationName: 'Havelock Jetty',
    startTime: '2026-12-01T10:00:00Z',
    endTime: '2026-12-03T10:00:00Z',
    turnaroundBufferMinutes: 60,
    holdDurationSeconds: 600,
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    remainingSeconds: 600,
    fullQuote: mockQuote,
    partialQuote: {
      ...mockQuote,
      requestedPaymentMode: 'PARTIAL_ADVANCE',
      advancePercentageApplied: 25,
      advanceDueNow: 590,
      pickupBalanceDue: 1770,
    },
  };

  const mockRazorpayCheckoutOrder: GatewayCheckoutOrderResponse = {
    paymentPublicId: 'pay_pub_123',
    bookingPublicId: 'bkg_pub_test_001',
    bookingReference: 'IR-2026-BKG001',
    gatewayProvider: 'razorpay',
    gatewayOrderId: 'order_rzp_mock_123',
    keyId: 'rzp_test_public_key_abc',
    amount: 2360,
    currency: 'INR',
    paymentType: 'RENTAL_FULL',
    liveEnabled: false,
    orderExpiresAt: new Date(Date.now() + 1800000).toISOString(),
    bookingPaymentDeadlineAt: new Date(Date.now() + 1800000).toISOString(),
  };

  const mockBookingCreationResult: BookingCreationResult = {
    booking: {
      summary: {
        publicId: 'bkg_pub_test_001',
        bookingReference: 'IR-2026-BKG001',
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
        bookingMode: 'INSTANT',
        status: 'PENDING_PAYMENT',
        paymentSummaryStatus: 'ORDER_CREATED',
        totalAmount: 2360,
        advanceAmountDue: 2360,
        pickupBalanceDue: 0,
        securityDepositAmount: 3000,
        depositCollectionMethod: 'AT_PICKUP',
        depositStatus: 'PENDING',
        overbookingStatus: 'NONE',
        currency: 'INR',
        cancellationDeadlineHours: 24,
        paymentDeadlineAt: null,
        createdAt: new Date().toISOString(),
      },
      driverDetail: {
        customerDlNumber: 'DL-AN01-2023-009988',
        customerEmergencyContact: '+919876543210',
        dlConfirmed: true,
        dlVerifiedAt: null,
        dlVerifiedByUserId: null,
        verified: false,
      },
      timeline: [],
      payments: [],
      refunds: [],
      securityDepositInspection: null,
      disruptionException: null,
    } as unknown as BookingDetailDto,
    checkoutOrder: mockRazorpayCheckoutOrder,
  };

  const mockUser = {
    id: 101,
    email: 'tourist@islandride.in',
    firstName: 'John',
    lastName: 'Doe',
    phone: '+91 98765 43210',
    roles: ['ROLE_TOURIST'],
  };

  beforeEach(async () => {
    MockRazorpayInstance.lastInstance = null;
    MockRazorpayInstance.lastOptions = null;

    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', [
      'getHold',
      'releaseHold',
      'createBooking',
      'initiateCheckoutOrder',
      'verifyPayment',
      'simulateMockPayment',
      'sendMockWebhook',
    ]);

    razorpayLoaderSpy = jasmine.createSpyObj<RazorpayLoaderService>('RazorpayLoaderService', ['load']);

    bookingServiceSpy.getHold.and.returnValue(of(mockHold));

    const mockAuthService = {
      currentUser: signal(mockUser),
    };

    await TestBed.configureTestingModule({
      imports: [TouristCheckoutComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'holdPublicId' ? 'hld_test123' : null),
              },
            },
          },
        },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: AuthService, useValue: mockAuthService },
        { provide: RazorpayLoaderService, useValue: razorpayLoaderSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(TouristCheckoutComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    if (component) {
      component.ngOnDestroy();
    }
  });

  it('should initialize hold details and render vehicle title and pricing', () => {
    fixture.detectChanges();

    expect(component.hold()).toEqual(mockHold);
    expect(component.secondsRemaining()).toBe(600);
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBeNull();
  });

  it('should display error when hold identifier is missing from route', () => {
    const route = TestBed.inject(ActivatedRoute);
    spyOn(route.snapshot.paramMap, 'get').and.returnValue(null);

    const missingParamFixture = TestBed.createComponent(TouristCheckoutComponent);
    missingParamFixture.detectChanges();

    expect(missingParamFixture.componentInstance.error()).toBe('Missing hold identifier.');
    expect(missingParamFixture.componentInstance.loading()).toBeFalse();
  });

  it('should display error when hold retrieval fails (expired or not found)', () => {
    bookingServiceSpy.getHold.and.returnValue(
      throwError(() => ({ error: { detail: 'Inventory hold expired or not found.' } }))
    );

    fixture.detectChanges();

    expect(component.hold()).toBeNull();
    expect(component.error()).toBe('Inventory hold expired or not found.');
    expect(component.loading()).toBeFalse();
  });

  it('should validate form fields before allowing proceedToPayment', () => {
    fixture.detectChanges();

    expect(component.canSubmit()).toBeFalse();

    component.customerDlNumber = 'DL';
    expect(component.canSubmit()).toBeFalse();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '123';
    expect(component.canSubmit()).toBeFalse();

    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = false;
    expect(component.canSubmit()).toBeFalse();

    component.dlConfirmed = true;
    expect(component.canSubmit()).toBeTrue();

    // Expired hold disables submit
    component.secondsRemaining.set(0);
    expect(component.canSubmit()).toBeFalse();
  });

  it('should initiate checkout and launch Razorpay when order gatewayProvider is razorpay', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    component.proceedToPayment();
    tick();

    expect(bookingServiceSpy.createBooking).toHaveBeenCalledWith(
      jasmine.objectContaining({
        holdPublicId: 'hld_test123',
        paymentMode: 'FULL',
        customerDlNumber: 'DL-AN01-2023-009988',
        customerEmergencyContact: '+91 98765 43210',
        dlConfirmed: true,
      }),
      jasmine.any(String)
    );

    expect(razorpayLoaderSpy.load).toHaveBeenCalled();
    expect(MockRazorpayInstance.lastInstance).not.toBeNull();
    expect(MockRazorpayInstance.lastInstance!.openCalled).toBeTrue();

    const options = MockRazorpayInstance.lastOptions!;
    expect(options.key).toBe('rzp_test_public_key_abc');
    expect(options.amount).toBe(236000); // 2360 INR * 100 paise
    expect(options.currency).toBe('INR');
    expect(options.order_id).toBe('order_rzp_mock_123');
    expect(options.name).toBe('IslandRide');
    expect(options.prefill?.name).toBe('John Doe');
    expect(options.prefill?.email).toBe('tourist@islandride.in');
    expect(options.prefill?.contact).toBe('+91 98765 43210');
    expect(options.theme?.color).toBe('#0f766e');
  }));

  it('should abort and show error when order.keyId is missing or empty', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    const orderWithoutKeyId: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      keyId: null,
    };
    bookingServiceSpy.createBooking.and.returnValue(
      of({ ...mockBookingCreationResult, checkoutOrder: orderWithoutKeyId })
    );

    component.proceedToPayment();
    tick();

    expect(razorpayLoaderSpy.load).not.toHaveBeenCalled();
    expect(component.bookingError()).toBe(
      'Payment gateway configuration error: public Key ID is missing.'
    );
    expect(component.submitting()).toBeFalse();
  }));

  it('should handle Razorpay SDK loading failure gracefully', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(Promise.reject(new Error('Network timeout loading SDK')));

    component.proceedToPayment();
    tick();

    expect(component.bookingError()).toContain('Network timeout loading SDK');
    expect(component.submitting()).toBeFalse();
  }));

  it('should verify payment on successful razorpay callback and navigate to booking details', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    const verificationResponse: PaymentVerificationResponse = {
      bookingPublicId: 'bkg_pub_test_001',
      bookingReference: 'IR-2026-BKG001',
      bookingStatus: 'CONFIRMED',
      paymentSummaryStatus: 'PAID_IN_FULL',
      overbookingDetected: false,
      payment: {} as any,
    };
    bookingServiceSpy.verifyPayment.and.returnValue(of(verificationResponse));

    component.proceedToPayment();
    tick();

    // Trigger success callback
    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_test_payment_999',
      razorpay_order_id: 'order_rzp_mock_123',
      razorpay_signature: 'test_crypto_signature_hex_123',
    };
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    tick();

    expect(bookingServiceSpy.verifyPayment).toHaveBeenCalledWith(
      {
        bookingPublicId: 'bkg_pub_test_001',
        gatewayOrderId: 'order_rzp_mock_123',
        gatewayPaymentId: 'pay_test_payment_999',
        gatewaySignature: 'test_crypto_signature_hex_123',
      },
      'ver_order_rzp_mock_123'
    );
    expect(router.navigate).toHaveBeenCalledWith(['/tourist/bookings', 'bkg_pub_test_001']);
    expect(component.submitting()).toBeFalse();
  }));

  it('should handle backend verification failure without navigating and display actionable message', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    bookingServiceSpy.verifyPayment.and.returnValue(
      throwError(() => ({ error: { detail: 'Invalid payment signature.' } }))
    );

    component.proceedToPayment();
    tick();

    const successPayload: RazorpayPaymentSuccessResponse = {
      razorpay_payment_id: 'pay_tampered_123',
      razorpay_order_id: 'order_rzp_mock_123',
      razorpay_signature: 'invalid_signature_hex',
    };
    MockRazorpayInstance.lastOptions!.handler!(successPayload);
    tick();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.bookingError()).toContain('Invalid payment signature');
    expect(component.bookingError()).toContain('webhook reconciliation');
    expect(component.submitting()).toBeFalse();
  }));

  it('should handle payment.failed event from Razorpay instance', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    component.proceedToPayment();
    tick();

    const failedPayload: RazorpayErrorResponse = {
      error: {
        code: 'BAD_REQUEST_ERROR',
        description: 'Customer card bank declined transaction',
        source: 'bank',
        step: 'payment_authorization',
        reason: 'payment_failed',
      },
    };

    const failHandler = MockRazorpayInstance.lastInstance!.events['payment.failed'];
    expect(failHandler).toBeDefined();
    failHandler(failedPayload);

    expect(component.bookingError()).toBe(
      'Payment failed: Customer card bank declined transaction. You can retry with another payment method.'
    );
    expect(component.submitting()).toBeFalse();
  }));

  it('should handle modal.ondismiss by showing a retry message while preserving created booking public ID', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(of(mockBookingCreationResult));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    component.proceedToPayment();
    tick();

    expect(component.createdBookingPublicId()).toBe('bkg_pub_test_001');

    // Trigger ondismiss
    MockRazorpayInstance.lastOptions!.modal!.ondismiss!();

    expect(component.submitting()).toBeFalse();
    expect(component.paymentStatus()).toBeNull();
    expect(component.bookingError()).toBe(
      'Payment was not completed. You can retry before your reservation hold expires.'
    );
    expect(component.createdBookingPublicId()).toBe('bkg_pub_test_001');
  }));

  it('should call initiateCheckoutOrder instead of createBooking on retry', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    // Simulate already created booking
    component.createdBookingPublicId.set('bkg_pub_test_001');

    bookingServiceSpy.initiateCheckoutOrder.and.returnValue(of(mockRazorpayCheckoutOrder));
    razorpayLoaderSpy.load.and.returnValue(
      Promise.resolve(MockRazorpayInstance as unknown as RazorpayConstructor)
    );

    component.proceedToPayment();
    tick();

    expect(bookingServiceSpy.createBooking).not.toHaveBeenCalled();
    expect(bookingServiceSpy.initiateCheckoutOrder).toHaveBeenCalledWith(
      'bkg_pub_test_001',
      jasmine.stringMatching(/^retry_pay_bkg_pub_test_001_\d+$/)
    );
    expect(MockRazorpayInstance.lastInstance!.openCalled).toBeTrue();
  }));

  it('should prevent duplicate submissions while in-flight', () => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;
    component.submitting.set(true);

    component.proceedToPayment();

    expect(bookingServiceSpy.createBooking).not.toHaveBeenCalled();
    expect(bookingServiceSpy.initiateCheckoutOrder).not.toHaveBeenCalled();
  });

  it('should open mock gateway simulator modal when order gatewayProvider is mock', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    const mockGatewayOrder: GatewayCheckoutOrderResponse = {
      ...mockRazorpayCheckoutOrder,
      gatewayProvider: 'mock',
      keyId: null,
    };
    bookingServiceSpy.createBooking.and.returnValue(
      of({ ...mockBookingCreationResult, checkoutOrder: mockGatewayOrder })
    );

    component.proceedToPayment();
    tick();

    expect(razorpayLoaderSpy.load).not.toHaveBeenCalled();
    expect(component.activeCheckoutOrder()).toEqual(mockGatewayOrder);
    expect(component.submitting()).toBeFalse();
  }));

  it('should navigate directly to booking detail when checkoutOrder is null (e.g. approval workflow)', fakeAsync(() => {
    fixture.detectChanges();

    component.customerDlNumber = 'DL-AN01-2023-009988';
    component.customerEmergencyContact = '+91 98765 43210';
    component.dlConfirmed = true;

    bookingServiceSpy.createBooking.and.returnValue(
      of({ ...mockBookingCreationResult, checkoutOrder: null })
    );

    component.proceedToPayment();
    tick();

    expect(router.navigate).toHaveBeenCalledWith(['/tourist/bookings', 'bkg_pub_test_001']);
    expect(component.submitting()).toBeFalse();
  }));

  it('should release hold early and navigate back to catalog on user confirmation', () => {
    fixture.detectChanges();

    spyOn(window, 'confirm').and.returnValue(true);
    bookingServiceSpy.releaseHold.and.returnValue(of(void 0));

    component.releaseHoldEarly();

    expect(bookingServiceSpy.releaseHold).toHaveBeenCalledWith('hld_test123');
    expect(router.navigate).toHaveBeenCalledWith(['/search']);
  });
});
