import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserDto } from '../../core/models/auth.models';
import { PartnerVehicleDto } from '../../core/models/vehicle.models';
import { PartnerFleetListComponent } from './partner-fleet-list.component';
import { PartnerVehicleFormComponent } from './partner-vehicle-form.component';
import { PartnerVehicleManageComponent } from './partner-vehicle-manage.component';

function buildPartnerVehicle(status: PartnerVehicleDto['status'] = 'DRAFT', bizStatus = 'APPROVED'): PartnerVehicleDto {
  return {
    publicId: 'veh_p1',
    businessPublicId: 'bus_1',
    businessTradeName: 'Andaman Rides',
    businessVerificationStatus: bizStatus,
    island: { id: 1, name: 'South Andaman', displayAlias: 'Sri Vijaya Puram (Port Blair)', code: 'PORT_BLAIR', description: null, displayOrder: 1, active: true },
    pickupLocation: null,
    returnLocation: null,
    allowDifferentReturnSameIsland: false,
    category: { id: 1, name: 'Scooter / Moped', code: 'SCOOTER', slug: 'scooters', vehicleType: 'TWO_WHEELER', description: null, iconRef: null, applicableAttributes: ['engineCapacityCc'], active: true, displayOrder: 1 },
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
    rentalInstructions: 'Carry licence',
    dailyRate: 500,
    securityDeposit: 2000,
    turnaroundBufferMinutes: 60,
    bookingMode: 'INSTANT',
    status,
    rejectionReason: null,
    suspensionReason: null,
    submittedAt: null,
    approvedAt: null,
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    features: ['Helmet Included'],
    images: [],
    documents: [],
    pricingOverview: {
      activePlan: {
        publicId: 'prc_1',
        currency: 'INR',
        hourlyRate: null,
        dailyRate: 500,
        weeklyRate: null,
        securityDeposit: 2000,
        minRentalHours: 6,
        maxRentalDays: 30,
        includedKmPerDay: null,
        extraKmCharge: null,
        extraHourCharge: null,
        cleaningFee: 0,
        deliveryFee: 0,
        additionalChargesDescription: null,
        lateReturnFeePerHour: null,
        lateReturnFlatPenalty: null,
        lateReturnGraceMinutes: 30,
        validFrom: null,
        validTo: null,
        active: true,
        gstPercentage: 5,
      },
      history: [],
      rules: [],
    },
    statusHistory: [{ id: 1, fromStatus: null, toStatus: status, reason: 'Created', changedBy: 1, createdAt: '2026-11-01T10:00:00Z' }],
  };
}

describe('Partner Fleet Portal Components', () => {
  let currentUserSignal = signal<UserDto | null>(null);

  beforeEach(() => {
    currentUserSignal = signal<UserDto | null>({
      publicId: 'usr_owner',
      email: 'owner@test.in',
      firstName: 'Owner',
      lastName: 'User',
      status: 'ACTIVE',
      roles: ['ROLE_PARTNER_OWNER'],
    });

    TestBed.configureTestingModule({
      imports: [PartnerFleetListComponent, PartnerVehicleFormComponent, PartnerVehicleManageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { currentUser: currentUserSignal } },
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('PartnerFleetListComponent should hide write controls for PARTNER_STAFF and show banner for unapproved business', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    currentUserSignal.set({
      publicId: 'usr_staff',
      email: 'staff@test.in',
      firstName: 'Staff',
      lastName: 'User',
      status: 'ACTIVE',
      roles: ['ROLE_PARTNER_STAFF'],
    });

    const fixture: ComponentFixture<PartnerFleetListComponent> = TestBed.createComponent(PartnerFleetListComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/locations/islands').flush([]);
    httpMock.expectOne('/api/v1/categories').flush([]);
    httpMock.expectOne('/api/v1/partners/vehicles/summary').flush({
      businessPublicId: 'bus_1',
      businessTradeName: 'Andaman Rides',
      businessVerificationStatus: 'APPROVED',
      totalVehicles: 1,
      activeVehicles: 0,
      pendingReviewVehicles: 0,
      draftVehicles: 1,
      pausedOrMaintenanceVehicles: 0,
      rejectedOrSuspendedVehicles: 0,
      archivedVehicles: 0,
      expiringDocumentsCount: 0,
    });
    httpMock.expectOne('/api/v1/partners/documents/expiring?withinDays=30').flush([]);
    httpMock.expectOne((r) => r.url === '/api/v1/partners/vehicles').flush({
      content: [buildPartnerVehicle('DRAFT')],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Partner Staff');
    expect(html.textContent).not.toContain('+ Add Vehicle');
    expect(html.textContent).not.toContain('Submit');

    // Now test OWNER on UNDER_REVIEW business
    currentUserSignal.set({
      publicId: 'usr_owner',
      email: 'owner@test.in',
      firstName: 'Owner',
      lastName: 'User',
      status: 'ACTIVE',
      roles: ['ROLE_PARTNER_OWNER'],
    });
    fixture.componentInstance.summary.set({
      businessPublicId: 'bus_1',
      businessTradeName: 'Andaman Rides',
      businessVerificationStatus: 'UNDER_REVIEW',
      totalVehicles: 0,
      activeVehicles: 0,
      pendingReviewVehicles: 0,
      draftVehicles: 0,
      pausedOrMaintenanceVehicles: 0,
      rejectedOrSuspendedVehicles: 0,
      archivedVehicles: 0,
      expiringDocumentsCount: 0,
    });
    fixture.detectChanges();
    expect(html.textContent).toContain('UNDER_REVIEW');
    expect(html.textContent).not.toContain('+ Add Vehicle');
  });

  it('PartnerFleetListComponent should submit a DRAFT vehicle for approved partner owner', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PartnerFleetListComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/locations/islands').flush([]);
    httpMock.expectOne('/api/v1/categories').flush([]);
    httpMock.expectOne('/api/v1/partners/vehicles/summary').flush({
      businessPublicId: 'bus_1',
      businessTradeName: 'Andaman Rides',
      businessVerificationStatus: 'APPROVED',
      totalVehicles: 1,
      activeVehicles: 0,
      pendingReviewVehicles: 0,
      draftVehicles: 1,
      pausedOrMaintenanceVehicles: 0,
      rejectedOrSuspendedVehicles: 0,
      archivedVehicles: 0,
      expiringDocumentsCount: 0,
    });
    httpMock.expectOne('/api/v1/partners/documents/expiring?withinDays=30').flush([]);
    httpMock.expectOne((r) => r.url === '/api/v1/partners/vehicles').flush({
      content: [buildPartnerVehicle('DRAFT')],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('+ Add Vehicle');

    const submitBtn = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Submit'
    ) as HTMLButtonElement;
    expect(submitBtn).toBeTruthy();
    submitBtn.click();

    const subReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_p1/submit');
    expect(subReq.request.method).toBe('POST');
    subReq.flush(buildPartnerVehicle('PENDING_REVIEW'));

    httpMock.expectOne('/api/v1/partners/vehicles/summary').flush({});
    httpMock.expectOne((r) => r.url === '/api/v1/partners/vehicles').flush({
      content: [buildPartnerVehicle('PENDING_REVIEW')],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    expect(fixture.componentInstance.message()).toContain('PENDING_REVIEW');
  });

  it('PartnerVehicleManageComponent should handle pricing save and 409 block conflict cleanly', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { paramMap: convertToParamMap({ publicId: 'veh_p1' }) } },
    });
    const httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(PartnerVehicleManageComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/document-types').flush([]);
    httpMock.expectOne('/api/v1/partners/vehicles/veh_p1').flush(buildPartnerVehicle('DRAFT'));
    httpMock.expectOne('/api/v1/partners/vehicles/veh_p1/pricing').flush(buildPartnerVehicle('DRAFT').pricingOverview);
    httpMock.expectOne((r) => r.url === '/api/v1/partners/vehicles/veh_p1/availability').flush({
      vehiclePublicId: 'veh_p1',
      from: '',
      to: '',
      availableForRequestedWindow: true,
      conflictReason: null,
      unavailableSlots: [],
      detailedBlocks: [],
    });

    fixture.detectChanges();
    fixture.componentInstance.savePricing();
    const saveReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_p1/pricing');
    expect(saveReq.request.method).toBe('PUT');
    saveReq.flush(buildPartnerVehicle('DRAFT').pricingOverview!.activePlan);
    httpMock.expectOne('/api/v1/partners/vehicles/veh_p1/pricing').flush(buildPartnerVehicle('DRAFT').pricingOverview);

    fixture.componentInstance.blockStartLocal = '2026-12-10T10:00';
    fixture.componentInstance.blockEndLocal = '2026-12-12T10:00';
    fixture.componentInstance.blockForm.reason = 'Servicing';
    fixture.componentInstance.createBlock();

    const blockReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_p1/availability/blocks');
    blockReq.flush({ detail: 'Vehicle already has an overlapping block' }, { status: 409, statusText: 'Conflict' });
    expect(fixture.componentInstance.error()).toContain('overlapping block');
  });

  it('PartnerVehicleFormComponent should load island hubs and sync same-hub return', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PartnerVehicleFormComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/locations/islands').flush([
      { id: 1, name: 'South Andaman', displayAlias: 'Port Blair', code: 'PORT_BLAIR', description: null, displayOrder: 1, active: true },
      { id: 2, name: 'Swaraj Dweep', displayAlias: 'Havelock', code: 'HAVELOCK', description: null, displayOrder: 2, active: true },
    ]);
    httpMock.expectOne('/api/v1/categories').flush([
      { id: 1, name: 'Scooter / Moped', code: 'SCOOTER', slug: 'scooters', vehicleType: 'TWO_WHEELER', description: null, iconRef: null, applicableAttributes: ['engineCapacityCc'], active: true, displayOrder: 1 },
    ]);
    httpMock.expectOne('/api/v1/locations/hubs?islandId=1').flush([
      { id: 1, publicId: 'loc_pb', islandId: 1, islandName: 'South Andaman', islandCode: 'PORT_BLAIR', name: 'Port Blair Hub', code: 'PB', address: 'PB', latitude: null, longitude: null, contactPhone: null, active: true },
    ]);

    fixture.detectChanges();
    fixture.componentInstance.form.pickupLocationPublicId = 'loc_pb';
    fixture.componentInstance.syncReturnHub();
    expect(fixture.componentInstance.form.returnLocationPublicId).toBe('loc_pb');

    fixture.componentInstance.form.islandId = 2;
    fixture.componentInstance.onIslandChange();
    httpMock.expectOne('/api/v1/locations/hubs?islandId=2').flush([]);
    expect(fixture.componentInstance.form.pickupLocationPublicId).toBe('');
  });
});

