import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserDto } from '../../core/models/auth.models';
import { AdminVehicleDto } from '../../core/models/vehicle.models';
import { AdminLayoutComponent } from '../../layouts/admin-layout/admin-layout.component';
import { AdminModerationQueueComponent } from './admin-moderation-queue.component';
import { AdminVehicleReviewComponent } from './admin-vehicle-review.component';

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

