import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PartnerFinancialService } from './partner-financial.service';
import { PartnerFinancialOverviewDto, CommissionDetailDto } from '../models/booking.models';

describe('PartnerFinancialService', () => {
  let service: PartnerFinancialService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        PartnerFinancialService
      ]
    });
    service = TestBed.inject(PartnerFinancialService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch financial overview with businessId param', () => {
    const mockOverview: PartnerFinancialOverviewDto = {
      businessId: 42,
      tradeName: 'Island Wheels',
      totalGrossBookings: 50000,
      totalCommissionDeducted: 2000,
      totalNetEarnings: 48000,
      onlineCollectedByPlatform: 35000,
      offlineCollectedByPartner: 15000,
      totalSettledPaidOut: 40000,
      pendingSettlementBalance: 8000,
      openReceivablesBalance: 600,
      totalCompletedBookings: 25,
      recentCommissions: [],
      recentSettlements: []
    };

    service.getFinancialOverview(42).subscribe((data) => {
      expect(data).toEqual(mockOverview);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/v1/partners/financials/overview');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('businessId')).toBe('42');
    req.flush(mockOverview);
  });

  it('should fetch financial overview without businessId param when omitted', () => {
    service.getFinancialOverview().subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/partners/financials/overview');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('businessId')).toBeFalse();
    req.flush({});
  });

  it('should fetch itemized commissions with businessId param', () => {
    const mockCommissions: CommissionDetailDto[] = [
      {
        commissionId: 101,
        bookingPublicId: 'bkg_abc123',
        bookingReference: 'BK-202611-001',
        vehicleTitle: 'Royal Enfield Classic',
        grossRentalAmount: 5000,
        commissionPercentage: 4,
        commissionAmount: 200,
        partnerEarnings: 4800,
        status: 'ACCRUED',
        createdAt: '2026-11-12T10:00:00Z'
      }
    ];

    service.getCommissions(42).subscribe((data) => {
      expect(data.length).toBe(1);
      expect(data[0].bookingReference).toBe('BK-202611-001');
      expect(data[0].commissionAmount).toBe(200);
      expect(data[0].grossRentalAmount).toBe(5000);
    });

    const req = httpMock.expectOne((r) => r.url === '/api/v1/partners/financials/commissions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('businessId')).toBe('42');
    req.flush(mockCommissions);
  });
});
