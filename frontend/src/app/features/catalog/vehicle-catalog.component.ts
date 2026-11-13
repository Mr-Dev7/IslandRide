import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/services/catalog.service';
import {
  BookingMode,
  FuelType,
  IslandDto,
  LocationDto,
  PublicVehicleSummaryDto,
  TransmissionType,
  VehicleCategoryDto,
  VehicleType,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-vehicle-catalog',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="ir-container">
        <header class="cat-header">
          <div>
            <h1>Explore Andaman & Nicobar Fleet</h1>
            <p class="muted">Compare verified partner scooters, bikes, sedans, SUVs & 4x4 jeeps across islands.</p>
          </div>
          <div class="sort-bar">
            <label for="sortBy" class="muted">Sort by</label>
            <select id="sortBy" [(ngModel)]="filter.sortBy" (change)="applyFilters(true)">
              <option value="newest">Newest Listed</option>
              <option value="price_asc">Price: Low to High</option>
              <option value="price_desc">Price: High to Low</option>
              <option value="seats_desc">Seating Capacity</option>
              <option value="year_desc">Model Year: Newest</option>
            </select>
          </div>
        </header>

        <div class="cat-layout">
          <aside class="ir-card filter-panel">
            <div class="filter-head">
              <h3>Filters</h3>
              <button type="button" class="link-btn" (click)="resetFilters()">Reset all</button>
            </div>

            <div class="field">
              <label for="q">Keyword / Model</label>
              <input id="q" type="text" [(ngModel)]="filter.q" placeholder="Activa, Thar, Innova..." (keyup.enter)="applyFilters(true)" />
            </div>

            <div class="field">
              <label for="islandId">Island</label>
              <select id="islandId" [(ngModel)]="filter.islandId" (change)="onIslandChange()">
                <option [ngValue]="null">All Islands</option>
                @for (isl of islands(); track isl.id) {
                  <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
                }
              </select>
            </div>

            <div class="field">
              <label for="hub">Pickup Hub</label>
              <select id="hub" [(ngModel)]="filter.pickupLocationPublicId" (change)="applyFilters(true)">
                <option value="">Any Hub</option>
                @for (hub of hubs(); track hub.publicId) {
                  <option [value]="hub.publicId">{{ hub.name }} ({{ hub.islandName }})</option>
                }
              </select>
            </div>

            <div class="field">
              <label for="category">Category</label>
              <select id="category" [(ngModel)]="filter.categoryId" (change)="applyFilters(true)">
                <option [ngValue]="null">All Categories</option>
                @for (cat of categories(); track cat.id) {
                  <option [ngValue]="cat.id">{{ cat.name }}</option>
                }
              </select>
            </div>

            <div class="field-row">
              <div class="field">
                <label for="vehicleType">Wheel Type</label>
                <select id="vehicleType" [(ngModel)]="filter.vehicleType" (change)="applyFilters(true)">
                  <option value="">Any</option>
                  <option value="TWO_WHEELER">2-Wheeler</option>
                  <option value="FOUR_WHEELER">4-Wheeler</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div class="field">
                <label for="fuelType">Fuel</label>
                <select id="fuelType" [(ngModel)]="filter.fuelType" (change)="applyFilters(true)">
                  <option value="">Any</option>
                  <option value="PETROL">Petrol</option>
                  <option value="DIESEL">Diesel</option>
                  <option value="ELECTRIC">Electric</option>
                </select>
              </div>
            </div>

            <div class="field-row">
              <div class="field">
                <label for="transmission">Transmission</label>
                <select id="transmission" [(ngModel)]="filter.transmissionType" (change)="applyFilters(true)">
                  <option value="">Any</option>
                  <option value="AUTOMATIC">Automatic</option>
                  <option value="MANUAL">Manual</option>
                </select>
              </div>
              <div class="field">
                <label for="minSeats">Min Seats</label>
                <input id="minSeats" type="number" min="1" max="15" [(ngModel)]="filter.minSeats" />
              </div>
            </div>

            <div class="field-row">
              <div class="field">
                <label for="minPrice">Min ₹/day</label>
                <input id="minPrice" type="number" min="0" [(ngModel)]="filter.minPrice" />
              </div>
              <div class="field">
                <label for="maxPrice">Max ₹/day</label>
                <input id="maxPrice" type="number" min="0" [(ngModel)]="filter.maxPrice" />
              </div>
            </div>

            <div class="field">
              <label for="bookingMode">Confirmation Mode</label>
              <select id="bookingMode" [(ngModel)]="filter.bookingMode" (change)="applyFilters(true)">
                <option value="">Any</option>
                <option value="INSTANT">Instant</option>
                <option value="APPROVAL">Partner Approval</option>
              </select>
            </div>

            <div class="field">
              <label for="availStart">Available From</label>
              <input id="availStart" type="datetime-local" [(ngModel)]="startLocal" />
            </div>
            <div class="field">
              <label for="availEnd">Available To</label>
              <input id="availEnd" type="datetime-local" [(ngModel)]="endLocal" />
            </div>

            <button type="button" class="ir-btn ir-btn-primary full-width" (click)="applyFilters(true)">
              Search & Filter
            </button>
          </aside>

          <section class="results-pane">
            @if (error()) {
              <div class="ir-alert ir-alert-error">{{ error() }}</div>
            }

            @if (loading()) {
              <div class="ir-card empty-state">Loading available island vehicles...</div>
            } @else if (vehicles().length === 0) {
              <div class="ir-card empty-state">
                <h3>No matching vehicles found</h3>
                <p class="muted">Try adjusting island, dates, category, or clearing your filters.</p>
              </div>
            } @else {
              <p class="muted result-count">
                Showing {{ vehicles().length }} of {{ totalElements() }} verified vehicles
              </p>
              <div class="vehicle-grid">
                @for (v of vehicles(); track v.publicId) {
                  <article class="ir-card vehicle-card">
                    <div class="card-media">
                      @if (v.primaryImageUrl) {
                        <img [src]="v.primaryImageUrl" [alt]="v.title" loading="lazy" />
                      } @else {
                        <div class="media-placeholder">{{ v.categoryName }}</div>
                      }
                      <span class="badge badge-island">{{ v.islandDisplayAlias || v.islandName }}</span>
                    </div>
                    <div class="card-body">
                      <div class="card-top">
                        <span class="badge">{{ v.categoryName }}</span>
                        @if (v.verifiedPartner) {
                          <span class="badge badge-verified">Verified Partner</span>
                        }
                      </div>
                      <h3>{{ v.title }}</h3>
                      <p class="muted small">{{ v.partnerTradeName }} · {{ v.pickupLocation?.name || v.islandName }}</p>
                      <div class="spec-pills">
                        <span>{{ v.transmissionType }}</span>
                        <span>{{ v.fuelType }}</span>
                        <span>{{ v.seatingCapacity }} Seats</span>
                        <span>{{ v.bookingMode }}</span>
                      </div>
                      <div class="card-foot">
                        <div>
                          <strong class="price">₹{{ v.dailyRate | number: '1.0-0' }}</strong>
                          <span class="muted">/day</span>
                          <div class="muted small">Deposit ₹{{ v.securityDeposit | number: '1.0-0' }}</div>
                        </div>
                        <a [routerLink]="['/vehicles', v.publicId]" class="ir-btn ir-btn-primary">
                          View Details
                        </a>
                      </div>
                    </div>
                  </article>
                }
              </div>

              @if (totalPages() > 1) {
                <div class="pagination-bar">
                  <button type="button" class="ir-btn ir-btn-outline" [disabled]="page() === 0" (click)="goToPage(page() - 1)">
                    Previous
                  </button>
                  <span class="muted">Page {{ page() + 1 }} of {{ totalPages() }}</span>
                  <button type="button" class="ir-btn ir-btn-outline" [disabled]="page() + 1 >= totalPages()" (click)="goToPage(page() + 1)">
                    Next
                  </button>
                </div>
              }
            }
          </section>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .cat-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
      .cat-header h1 { margin: 0 0 .25rem; font-size: 1.65rem; color: var(--ir-navy); font-weight: 700; }
      .sort-bar { display: flex; align-items: center; gap: .5rem; }
      .sort-bar select { border: 1px solid var(--ir-border); border-radius: var(--ir-radius-sm); padding: 0.4rem 0.65rem; font-size: 0.875rem; background: #ffffff; color: var(--ir-charcoal); }
      .cat-layout { display: grid; grid-template-columns: 290px 1fr; gap: 1.5rem; align-items: start; }
      .filter-panel { padding: 1.25rem; display: flex; flex-direction: column; gap: .85rem; background: var(--ir-surface); border: 1px solid var(--ir-border); border-radius: var(--ir-radius-md); box-shadow: var(--ir-shadow-sm); }
      .filter-head { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--ir-border); padding-bottom: 0.5rem; }
      .filter-head h3 { margin: 0; font-size: 1.05rem; color: var(--ir-navy); font-weight: 700; }
      .field { display: flex; flex-direction: column; gap: 0.25rem; }
      .field label { font-size: 0.75rem; font-weight: 600; color: var(--ir-charcoal); }
      .field input, .field select { width: 100%; padding: 0.45rem 0.65rem; border: 1px solid var(--ir-border); border-radius: var(--ir-radius-sm); font-size: 0.875rem; background: #ffffff; color: var(--ir-charcoal); box-sizing: border-box; }
      .field input:focus, .field select:focus { outline: none; border-color: var(--ir-teal); box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.15); }
      .field-row { display: grid; grid-template-columns: 1fr 1fr; gap: .6rem; }
      .vehicle-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1.25rem; }
      .vehicle-card { overflow: hidden; display: flex; flex-direction: column; padding: 0; background: var(--ir-surface); border: 1px solid var(--ir-border); border-radius: var(--ir-radius-md); transition: transform var(--ir-transition), box-shadow var(--ir-transition); box-shadow: var(--ir-shadow-sm); }
      .vehicle-card:hover { transform: translateY(-2px); box-shadow: var(--ir-shadow-md); border-color: var(--ir-teal); }
      .card-media { position: relative; height: 185px; background: var(--ir-sea-mist); overflow: hidden; }
      .card-media img { width: 100%; height: 100%; object-fit: cover; display: block; }
      .media-placeholder { height: 100%; display: flex; align-items: center; justify-content: center; font-weight: 600; color: var(--ir-navy); }
      .badge-island { position: absolute; left: .75rem; bottom: .75rem; background: rgba(18, 59, 74, 0.9); color: #fff; padding: 3px 8px; border-radius: var(--ir-radius-sm); font-size: 0.75rem; font-weight: 600; }
      .badge-verified { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
      .card-body { padding: 1.1rem; display: flex; flex-direction: column; gap: .55rem; flex: 1; }
      .card-top { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
      .card-body h3 { margin: 0; font-size: 1.1rem; color: var(--ir-navy); font-weight: 700; }
      .spec-pills { display: flex; flex-wrap: wrap; gap: .4rem; font-size: .75rem; color: var(--ir-charcoal); }
      .spec-pills span { background: var(--ir-sea-mist); padding: .25rem .5rem; border-radius: var(--ir-radius-sm); border: 1px solid #c2e0d8; font-weight: 500; }
      .card-foot { margin-top: auto; padding-top: .75rem; border-top: 1px solid var(--ir-border); display: flex; justify-content: space-between; align-items: center; }
      .price { font-size: 1.35rem; color: var(--ir-navy); font-weight: 800; }
      .pagination-bar { margin-top: 1.5rem; display: flex; justify-content: center; align-items: center; gap: 1rem; }
      .empty-state { padding: 2.5rem; text-align: center; background: var(--ir-surface); border: 1px solid var(--ir-border); border-radius: var(--ir-radius-md); }
      .result-count { margin: 0 0 .85rem; font-size: 0.875rem; }
      @media (max-width: 900px) { .cat-layout { grid-template-columns: 1fr; } }
    `,
  ],
})
export class VehicleCatalogComponent implements OnInit {
  private readonly catalogService = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  islands = signal<IslandDto[]>([]);
  hubs = signal<LocationDto[]>([]);
  categories = signal<VehicleCategoryDto[]>([]);
  vehicles = signal<PublicVehicleSummaryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  page = signal<number>(0);
  totalPages = signal<number>(0);
  totalElements = signal<number>(0);

  startLocal = '';
  endLocal = '';

  filter: {
    q: string;
    islandId: number | null;
    pickupLocationPublicId: string;
    categoryId: number | null;
    vehicleType: VehicleType | '';
    fuelType: FuelType | '';
    transmissionType: TransmissionType | '';
    minSeats: number | null;
    minPrice: number | null;
    maxPrice: number | null;
    bookingMode: BookingMode | '';
    sortBy: 'newest' | 'price_asc' | 'price_desc' | 'seats_desc' | 'year_desc';
  } = {
    q: '',
    islandId: null,
    pickupLocationPublicId: '',
    categoryId: null,
    vehicleType: '',
    fuelType: '',
    transmissionType: '',
    minSeats: null,
    minPrice: null,
    maxPrice: null,
    bookingMode: '',
    sortBy: 'newest',
  };

  ngOnInit(): void {
    this.catalogService.getIslands().subscribe({ next: (data) => this.islands.set(data) });
    this.catalogService.getCategories().subscribe({ next: (data) => this.categories.set(data) });
    this.catalogService.getHubs().subscribe({ next: (data) => this.hubs.set(data) });

    this.route.queryParams.subscribe((params) => {
      if (params['islandId']) this.filter.islandId = Number(params['islandId']);
      if (params['categoryId']) this.filter.categoryId = Number(params['categoryId']);
      if (params['pickupLocationPublicId']) this.filter.pickupLocationPublicId = params['pickupLocationPublicId'];
      if (params['q']) this.filter.q = params['q'];
      if (params['vehicleType']) this.filter.vehicleType = params['vehicleType'];
      if (params['sortBy']) this.filter.sortBy = params['sortBy'];
      if (params['startLocal']) this.startLocal = params['startLocal'];
      if (params['endLocal']) this.endLocal = params['endLocal'];
      if (this.filter.islandId) {
        this.catalogService.getHubs(this.filter.islandId).subscribe({ next: (h) => this.hubs.set(h) });
      }
      this.fetchVehicles();
    });
  }

  onIslandChange(): void {
    this.filter.pickupLocationPublicId = '';
    this.catalogService.getHubs(this.filter.islandId).subscribe({ next: (h) => this.hubs.set(h) });
    this.applyFilters(true);
  }

  applyFilters(resetPage: boolean): void {
    if (resetPage) this.page.set(0);
    this.error.set(null);

    if ((this.startLocal && !this.endLocal) || (!this.startLocal && this.endLocal)) {
      this.error.set('Please select both Available From and Available To timestamps.');
      return;
    }

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        islandId: this.filter.islandId || null,
        categoryId: this.filter.categoryId || null,
        q: this.filter.q || null,
        sortBy: this.filter.sortBy,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.fetchVehicles();
  }

  resetFilters(): void {
    this.filter = {
      q: '',
      islandId: null,
      pickupLocationPublicId: '',
      categoryId: null,
      vehicleType: '',
      fuelType: '',
      transmissionType: '',
      minSeats: null,
      minPrice: null,
      maxPrice: null,
      bookingMode: '',
      sortBy: 'newest',
    };
    this.startLocal = '';
    this.endLocal = '';
    this.catalogService.getHubs().subscribe({ next: (h) => this.hubs.set(h) });
    this.applyFilters(true);
  }

  goToPage(nextPage: number): void {
    this.page.set(nextPage);
    this.fetchVehicles();
  }

  private fetchVehicles(): void {
    this.loading.set(true);
    this.error.set(null);

    const startTime = this.startLocal ? new Date(this.startLocal).toISOString() : null;
    const endTime = this.endLocal ? new Date(this.endLocal).toISOString() : null;

    this.catalogService
      .searchVehicles({
        q: this.filter.q || null,
        islandId: this.filter.islandId,
        pickupLocationPublicId: this.filter.pickupLocationPublicId || null,
        categoryId: this.filter.categoryId,
        vehicleType: (this.filter.vehicleType || null) as VehicleType | null,
        fuelType: (this.filter.fuelType || null) as FuelType | null,
        transmissionType: (this.filter.transmissionType || null) as TransmissionType | null,
        minSeats: this.filter.minSeats,
        minPrice: this.filter.minPrice,
        maxPrice: this.filter.maxPrice,
        bookingMode: (this.filter.bookingMode || null) as BookingMode | null,
        startTime,
        endTime,
        sortBy: this.filter.sortBy,
        page: this.page(),
        size: 12,
      })
      .subscribe({
        next: (res) => {
          this.vehicles.set(res.content);
          this.totalPages.set(res.totalPages);
          this.totalElements.set(res.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.detail || 'Could not load vehicle catalog right now.');
          this.loading.set(false);
        },
      });
  }
}
