import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogService } from './catalog.service';
import { PartnerInventoryService } from './partner-inventory.service';
import { AdminFleetService } from './admin-fleet.service';

describe('Phase 5 Frontend Services', () => {
  let httpMock: HttpTestingController;
  let catalog: CatalogService;
  let partner: PartnerInventoryService;
  let admin: AdminFleetService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    catalog = TestBed.inject(CatalogService);
    partner = TestBed.inject(PartnerInventoryService);
    admin = TestBed.inject(AdminFleetService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('CatalogService should fetch islands, hubs, and search vehicles with filters', () => {
    catalog.getIslands().subscribe();
    httpMock.expectOne('/api/v1/locations/islands').flush([]);

    catalog.getHubs(2).subscribe();
    const hubsReq = httpMock.expectOne((r) => r.url === '/api/v1/locations/hubs');
    expect(hubsReq.request.params.get('islandId')).toBe('2');
    hubsReq.flush([]);

    catalog.searchVehicles({ islandId: 1, sortBy: 'price_asc', q: 'Activa', page: 0, size: 12 }).subscribe();
    const searchReq = httpMock.expectOne((r) => r.url === '/api/v1/vehicles');
    expect(searchReq.request.params.get('islandId')).toBe('1');
    expect(searchReq.request.params.get('sortBy')).toBe('price_asc');
    expect(searchReq.request.params.get('q')).toBe('Activa');
    searchReq.flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it('PartnerInventoryService should invoke CRUD, pricing, availability, media, and hubs endpoints', () => {
    partner.getSummary().subscribe();
    httpMock.expectOne('/api/v1/partners/vehicles/summary').flush({});

    partner.submitForReview('veh_123').subscribe();
    const subReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_123/submit');
    expect(subReq.request.method).toBe('POST');
    subReq.flush({});

    partner.savePricing('veh_123', { dailyRate: 650, securityDeposit: 2000 }).subscribe();
    const priceReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_123/pricing');
    expect(priceReq.request.method).toBe('PUT');
    priceReq.flush({});

    const fakeFile = new File(['abc'], 'photo.jpg', { type: 'image/jpeg' });
    partner.uploadImages('veh_123', [fakeFile]).subscribe();
    const imgReq = httpMock.expectOne('/api/v1/partners/vehicles/veh_123/images');
    expect(imgReq.request.body instanceof FormData).toBeTrue();
    imgReq.flush([]);

    partner.getBusinessLocations().subscribe();
    httpMock.expectOne('/api/v1/partners/locations').flush([]);

    partner.updateBusinessLocations({ locations: [{ locationPublicId: 'loc_1', primary: true, pickupAvailable: true, returnAvailable: true }] }).subscribe();
    const hubPut = httpMock.expectOne('/api/v1/partners/locations');
    expect(hubPut.request.method).toBe('PUT');
    hubPut.flush([]);
  });

  it('AdminFleetService should invoke moderation, document verification, and catalog config endpoints', () => {
    admin.listModerationQueue({ status: 'PENDING_REVIEW' }).subscribe();
    const qReq = httpMock.expectOne((r) => r.url === '/api/v1/admin/vehicles');
    expect(qReq.request.params.get('status')).toBe('PENDING_REVIEW');
    qReq.flush({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0, first: true, last: true });

    admin.rejectVehicle('veh_1', 'Blurry RC document').subscribe();
    const rejReq = httpMock.expectOne('/api/v1/admin/vehicles/veh_1/reject');
    expect(rejReq.request.body.reason).toBe('Blurry RC document');
    rejReq.flush({});

    admin.verifyDocument('veh_1', 'doc_1', 'VERIFIED').subscribe();
    const docReq = httpMock.expectOne('/api/v1/admin/vehicles/veh_1/documents/doc_1/verify');
    expect(docReq.request.method).toBe('PATCH');
    docReq.flush({});

    admin.updateCategoryStatus(3, false).subscribe();
    const catReq = httpMock.expectOne((r) => r.url === '/api/v1/admin/categories/3/status');
    expect(catReq.request.params.get('active')).toBe('false');
    catReq.flush({});
  });
});

