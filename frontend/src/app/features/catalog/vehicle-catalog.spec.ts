import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { VehicleCatalogComponent } from './vehicle-catalog.component';
import { VehicleDetailComponent } from './vehicle-detail.component';

describe('Public Vehicle Catalog Components', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VehicleCatalogComponent, VehicleDetailComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('VehicleCatalogComponent should render island vehicles and filter by island', () => {
    const httpMock = TestBed.inject(HttpTestingController);
    const fixture: ComponentFixture<VehicleCatalogComponent> = TestBed.createComponent(VehicleCatalogComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/locations/islands').flush([
      { id: 1, name: 'South Andaman (Port Blair)', displayAlias: 'Sri Vijaya Puram (Port Blair)', code: 'PORT_BLAIR', description: null, displayOrder: 1, active: true },
    ]);
    httpMock.expectOne('/api/v1/categories').flush([
      { id: 1, name: 'Scooter / Moped', code: 'SCOOTER', slug: 'scooters', vehicleType: 'TWO_WHEELER', description: null, iconRef: null, applicableAttributes: [], active: true, displayOrder: 1 },
    ]);
    httpMock.expectOne('/api/v1/locations/hubs').flush([]);

    const initSearch = httpMock.expectOne((r) => r.url === '/api/v1/vehicles');
    initSearch.flush({
      content: [
        {
          publicId: 'veh_1',
          title: 'Honda Activa 6G 2024',
          make: 'Honda',
          model: 'Activa 6G',
          modelYear: 2024,
          categoryId: 1,
          categoryName: 'Scooter / Moped',
          categoryCode: 'SCOOTER',
          categorySlug: 'scooters',
          vehicleType: 'TWO_WHEELER',
          islandId: 1,
          islandName: 'South Andaman (Port Blair)',
          islandDisplayAlias: 'Sri Vijaya Puram (Port Blair)',
          islandCode: 'PORT_BLAIR',
          pickupLocation: null,
          returnLocation: null,
          allowDifferentReturnSameIsland: false,
          transmissionType: 'AUTOMATIC',
          fuelType: 'PETROL',
          seatingCapacity: 2,
          vehicleCondition: 'EXCELLENT',
          dailyRate: 500,
          securityDeposit: 2000,
          currency: 'INR',
          bookingMode: 'INSTANT',
          partnerPublicId: 'bus_1',
          partnerTradeName: 'Andaman Rides',
          verifiedPartner: true,
          primaryImageUrl: null,
          features: ['Helmet Included'],
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    });

    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Honda Activa 6G 2024');
    expect(el.textContent).toContain('Andaman Rides');
    expect(el.textContent).toContain('500');

    // Validate partial date range error guard
    fixture.componentInstance.startLocal = '2026-12-01T10:00';
    fixture.componentInstance.endLocal = '';
    fixture.componentInstance.applyFilters(true);
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toContain('Available From and Available To');
  });

  it('VehicleDetailComponent should render specs, pricing, GST and availability calendar', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: { paramMap: convertToParamMap({ publicId: 'veh_detail_1' }) },
        queryParams: of({}),
      },
    });
    const httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(VehicleDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/vehicles/veh_detail_1').flush({
      publicId: 'veh_detail_1',
      title: 'Mahindra Thar 4x4 2024',
      make: 'Mahindra',
      model: 'Thar',
      modelYear: 2024,
      description: 'Island explorer',
      color: 'Red',
      category: { id: 6, name: 'Jeep / 4x4', code: 'JEEP', slug: 'jeeps', vehicleType: 'FOUR_WHEELER', description: null, iconRef: null, applicableAttributes: [], active: true, displayOrder: 6 },
      island: { id: 2, name: 'Swaraj Dweep (Havelock)', displayAlias: 'Havelock Island', code: 'HAVELOCK', description: null, displayOrder: 2, active: true },
      pickupLocation: null,
      returnLocation: null,
      allowDifferentReturnSameIsland: false,
      transmissionType: 'MANUAL',
      fuelType: 'DIESEL',
      seatingCapacity: 4,
      mileageKmpl: 15,
      engineCapacityCc: 2184,
      vehicleCondition: 'EXCELLENT',
      rentalInstructions: 'Carry original licence',
      bookingMode: 'INSTANT',
      turnaroundBufferMinutes: 60,
      partnerPublicId: 'bus_1',
      partnerTradeName: 'Havelock Cruisers',
      verifiedPartner: true,
      features: ['AC', '4x4'],
      images: [],
      pricingPlan: {
        publicId: 'prc_1',
        currency: 'INR',
        hourlyRate: null,
        dailyRate: 3500,
        weeklyRate: 21000,
        securityDeposit: 5000,
        minRentalHours: 24,
        maxRentalDays: 30,
        includedKmPerDay: 150,
        extraKmCharge: 18,
        extraHourCharge: 300,
        cleaningFee: 200,
        deliveryFee: 0,
        additionalChargesDescription: null,
        lateReturnFeePerHour: 400,
        lateReturnFlatPenalty: null,
        lateReturnGraceMinutes: 30,
        validFrom: null,
        validTo: null,
        active: true,
        gstPercentage: 5,
      },
      pricingRules: [],
    });

    httpMock.expectOne((r) => r.url === '/api/v1/vehicles/veh_detail_1/availability').flush({
      vehiclePublicId: 'veh_detail_1',
      from: '2026-12-01T00:00:00Z',
      to: '2026-12-15T00:00:00Z',
      availableForRequestedWindow: false,
      conflictReason: 'Booked / Blocked during selected dates',
      unavailableSlots: [{ startTime: '2026-12-05T00:00:00Z', endTime: '2026-12-07T00:00:00Z', slotType: 'BLOCK', displayLabel: 'Maintenance' }],
      detailedBlocks: [],
    });

    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Mahindra Thar 4x4 2024');
    expect(el.textContent).toContain('GST');
    expect(el.textContent).toContain('Not available');
  });
});

