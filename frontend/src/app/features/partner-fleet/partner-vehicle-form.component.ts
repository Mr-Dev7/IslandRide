import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/services/catalog.service';
import { PartnerInventoryService } from '../../core/services/partner-inventory.service';
import {
  BookingMode,
  FuelType,
  IslandDto,
  LocationDto,
  SaveVehicleRequest,
  TransmissionType,
  VehicleCategoryDto,
  VehicleCondition,
  VehicleStatus,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-partner-vehicle-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <a [routerLink]="isEditMode() ? ['/partner/fleet', vehiclePublicId()] : ['/partner/fleet']" class="link-btn">← Back</a>
          <h1>{{ isEditMode() ? 'Edit Vehicle Specifications' : 'Add New Rental Vehicle' }}</h1>
        </div>
      </div>

      @if (isEditMode() && currentStatus() === 'ACTIVE') {
        <div class="ir-alert ir-alert-warn">
          <strong>Re-Approval Notice:</strong> This listing is currently <strong>ACTIVE</strong>. Changing make, model, year, category, registration number, island, or pickup hub will automatically return it to <strong>PENDING_REVIEW</strong> for admin verification.
        </div>
      }

      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      @if (loading()) {
        <div class="ir-card pad muted">Loading vehicle form...</div>
      } @else {
        <form class="ir-card pad" (ngSubmit)="save()">
          <h3>1. Operating Location & Category</h3>
          <div class="field-row">
            <div class="field">
              <label for="v-island">Operating Island *</label>
              <select id="v-island" [(ngModel)]="form.islandId" name="islandId" required (change)="onIslandChange()">
                <option [ngValue]="0" disabled>Select island</option>
                @for (isl of islands(); track isl.id) {
                  <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="v-cat">Vehicle Category *</label>
              <select id="v-cat" [(ngModel)]="form.categoryId" name="categoryId" required>
                <option [ngValue]="0" disabled>Select category</option>
                @for (cat of categories(); track cat.id) {
                  <option [ngValue]="cat.id">{{ cat.name }} ({{ cat.vehicleType }})</option>
                }
              </select>
            </div>
          </div>

          @if (selectedCategory(); as cat) {
            <div class="ir-alert ir-alert-info small">
              <strong>{{ cat.name }}</strong> · Recommended specs: {{ cat.applicableAttributes.join(', ') || 'standard attributes' }}.
            </div>
          }

          <div class="field-row">
            <div class="field">
              <label for="v-pickup">Pickup Hub (on selected island)</label>
              <select id="v-pickup" [(ngModel)]="form.pickupLocationPublicId" name="pickupLocationPublicId" (change)="syncReturnHub()">
                <option value="">Island-wide / Default Partner Hub</option>
                @for (hub of hubs(); track hub.publicId) {
                  <option [value]="hub.publicId">{{ hub.name }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="v-return">Return Hub (same island only)</label>
              <select id="v-return" [(ngModel)]="form.returnLocationPublicId" name="returnLocationPublicId" [disabled]="!form.allowDifferentReturnSameIsland">
                <option value="">Same as Pickup Hub</option>
                @for (hub of hubs(); track hub.publicId) {
                  <option [value]="hub.publicId">{{ hub.name }}</option>
                }
              </select>
            </div>
          </div>

          <label class="check-line">
            <input type="checkbox" [(ngModel)]="form.allowDifferentReturnSameIsland" name="allowDiff" (change)="syncReturnHub()" />
            Allow returning vehicle at a different hub on the same island
          </label>

          <h3 class="mt-md">2. Vehicle Identity & Specifications</h3>
          <div class="field-row">
            <div class="field">
              <label for="v-title">Listing Title *</label>
              <input id="v-title" type="text" [(ngModel)]="form.title" name="title" required placeholder="e.g. Honda Activa 6G 2024 Pearl White" />
            </div>
            <div class="field">
              <label for="v-reg">Registration Number *</label>
              <input id="v-reg" type="text" [(ngModel)]="form.registrationNumber" name="registrationNumber" required placeholder="e.g. AN01AB1234" />
            </div>
          </div>

          <div class="field-row-3">
            <div class="field"><label for="v-make">Make *</label><input id="v-make" type="text" [(ngModel)]="form.make" name="make" required /></div>
            <div class="field"><label for="v-model">Model *</label><input id="v-model" type="text" [(ngModel)]="form.model" name="model" required /></div>
            <div class="field"><label for="v-year">Model Year *</label><input id="v-year" type="number" min="2005" max="2035" [(ngModel)]="form.modelYear" name="modelYear" required /></div>
          </div>

          <div class="field-row-3">
            <div class="field">
              <label for="v-trans">Transmission *</label>
              <select id="v-trans" [(ngModel)]="form.transmissionType" name="transmissionType">
                <option value="AUTOMATIC">Automatic</option>
                <option value="MANUAL">Manual</option>
              </select>
            </div>
            <div class="field">
              <label for="v-fuel">Fuel Type *</label>
              <select id="v-fuel" [(ngModel)]="form.fuelType" name="fuelType">
                <option value="PETROL">Petrol</option>
                <option value="DIESEL">Diesel</option>
                <option value="ELECTRIC">Electric</option>
              </select>
            </div>
            <div class="field">
              <label for="v-seats">Seating Capacity *</label>
              <input id="v-seats" type="number" min="1" max="20" [(ngModel)]="form.seatingCapacity" name="seatingCapacity" required />
            </div>
          </div>

          <div class="field-row-3">
            <div class="field"><label for="v-color">Color</label><input id="v-color" type="text" [(ngModel)]="form.color" name="color" /></div>
            <div class="field"><label for="v-mileage">Mileage (km/l)</label><input id="v-mileage" type="number" step="0.1" [(ngModel)]="form.mileageKmpl" name="mileageKmpl" /></div>
            <div class="field"><label for="v-cc">Engine Capacity (cc)</label><input id="v-cc" type="number" [(ngModel)]="form.engineCapacityCc" name="engineCapacityCc" /></div>
          </div>

          <div class="field-row-3">
            <div class="field">
              <label for="v-cond">Condition</label>
              <select id="v-cond" [(ngModel)]="form.vehicleCondition" name="vehicleCondition">
                <option value="EXCELLENT">Excellent</option>
                <option value="GOOD">Good</option>
                <option value="FAIR">Fair</option>
              </select>
            </div>
            <div class="field">
              <label for="v-mode">Confirmation Mode</label>
              <select id="v-mode" [(ngModel)]="form.bookingMode" name="bookingMode">
                <option value="INSTANT">Instant</option>
                <option value="APPROVAL">Partner Approval</option>
              </select>
            </div>
            <div class="field">
              <label for="v-buf">Turnaround Buffer (mins)</label>
              <input id="v-buf" type="number" min="0" max="720" [(ngModel)]="form.turnaroundBufferMinutes" name="turnaroundBufferMinutes" />
            </div>
          </div>

          <h3 class="mt-md">3. Base Pricing & Rental Instructions</h3>
          <div class="field-row">
            <div class="field"><label for="v-rate">Base Daily Rate (INR ₹) *</label><input id="v-rate" type="number" min="50" step="0.01" [(ngModel)]="form.dailyRate" name="dailyRate" required /></div>
            <div class="field"><label for="v-dep">Security Deposit (INR ₹)</label><input id="v-dep" type="number" min="0" step="0.01" [(ngModel)]="form.securityDeposit" name="securityDeposit" /></div>
          </div>

          <div class="field">
            <label for="v-features">Included Features (comma separated)</label>
            <input id="v-features" type="text" [(ngModel)]="featuresCsv" name="featuresCsv" placeholder="Helmet Included, USB Charger, Phone Mount" />
          </div>

          <div class="field">
            <label for="v-desc">Description</label>
            <textarea id="v-desc" rows="2" [(ngModel)]="form.description" name="description"></textarea>
          </div>

          <div class="field">
            <label for="v-instr">Pickup / Rental Instructions</label>
            <textarea id="v-instr" rows="2" [(ngModel)]="form.rentalInstructions" name="rentalInstructions" placeholder="Carry original driving licence and Aadhaar/Passport at pickup."></textarea>
          </div>

          <div class="actions-row mt-md">
            <button type="submit" class="ir-btn ir-btn-primary" [disabled]="saving()">
              {{ saving() ? 'Saving...' : (isEditMode() ? 'Save Changes' : 'Create Vehicle Draft') }}
            </button>
            <a [routerLink]="isEditMode() ? ['/partner/fleet', vehiclePublicId()] : ['/partner/fleet']" class="ir-btn ir-btn-outline">Cancel</a>
          </div>
        </form>
      }
    </div>
  `,
  styles: [`
    h3 { font-size: 1rem; margin-bottom: 0.75rem; color: #1e293b; }
    .check-line { display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; margin-bottom: 0.5rem; }
  `],
})
export class PartnerVehicleFormComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalogService = inject(CatalogService);
  private readonly partnerService = inject(PartnerInventoryService);

  islands = signal<IslandDto[]>([]);
  categories = signal<VehicleCategoryDto[]>([]);
  hubs = signal<LocationDto[]>([]);
  vehiclePublicId = signal<string | null>(null);
  currentStatus = signal<VehicleStatus | null>(null);
  loading = signal<boolean>(false);
  saving = signal<boolean>(false);
  error = signal<string | null>(null);

  isEditMode = computed(() => !!this.vehiclePublicId());
  selectedCategory = computed(() => this.categories().find((c) => c.id === this.form.categoryId) || null);

  featuresCsv = '';

  form: {
    islandId: number;
    categoryId: number;
    pickupLocationPublicId: string;
    returnLocationPublicId: string;
    allowDifferentReturnSameIsland: boolean;
    title: string;
    make: string;
    model: string;
    modelYear: number;
    description: string;
    registrationNumber: string;
    color: string;
    transmissionType: TransmissionType;
    fuelType: FuelType;
    seatingCapacity: number;
    mileageKmpl: number | null;
    engineCapacityCc: number | null;
    vehicleCondition: VehicleCondition;
    rentalInstructions: string;
    dailyRate: number;
    securityDeposit: number;
    turnaroundBufferMinutes: number;
    bookingMode: BookingMode;
  } = {
    islandId: 0,
    categoryId: 0,
    pickupLocationPublicId: '',
    returnLocationPublicId: '',
    allowDifferentReturnSameIsland: false,
    title: '',
    make: '',
    model: '',
    modelYear: 2024,
    description: '',
    registrationNumber: '',
    color: '',
    transmissionType: 'AUTOMATIC',
    fuelType: 'PETROL',
    seatingCapacity: 2,
    mileageKmpl: null,
    engineCapacityCc: null,
    vehicleCondition: 'EXCELLENT',
    rentalInstructions: '',
    dailyRate: 500,
    securityDeposit: 2000,
    turnaroundBufferMinutes: 60,
    bookingMode: 'INSTANT',
  };

  ngOnInit(): void {
    this.catalogService.getIslands().subscribe({
      next: (isl) => {
        this.islands.set(isl);
        if (!this.isEditMode() && !this.form.islandId && isl.length > 0) {
          this.form.islandId = isl[0].id;
          this.loadHubs(this.form.islandId);
        }
      },
    });
    this.catalogService.getCategories().subscribe({
      next: (cats) => {
        this.categories.set(cats);
        if (!this.isEditMode() && !this.form.categoryId && cats.length > 0) {
          this.form.categoryId = cats[0].id;
        }
      },
    });

    const pubId = this.route.snapshot.paramMap.get('publicId');
    if (pubId) {
      this.vehiclePublicId.set(pubId);
      this.loading.set(true);
      this.partnerService.getVehicle(pubId).subscribe({
        next: (v) => {
          this.currentStatus.set(v.status);
          this.form = {
            islandId: v.island.id,
            categoryId: v.category.id,
            pickupLocationPublicId: v.pickupLocation?.publicId || '',
            returnLocationPublicId: v.returnLocation?.publicId || '',
            allowDifferentReturnSameIsland: v.allowDifferentReturnSameIsland,
            title: v.title,
            make: v.make,
            model: v.model,
            modelYear: v.modelYear,
            description: v.description || '',
            registrationNumber: v.registrationNumber,
            color: v.color || '',
            transmissionType: v.transmissionType,
            fuelType: v.fuelType,
            seatingCapacity: v.seatingCapacity,
            mileageKmpl: v.mileageKmpl,
            engineCapacityCc: v.engineCapacityCc,
            vehicleCondition: v.vehicleCondition,
            rentalInstructions: v.rentalInstructions || '',
            dailyRate: v.dailyRate,
            securityDeposit: v.securityDeposit,
            turnaroundBufferMinutes: v.turnaroundBufferMinutes,
            bookingMode: v.bookingMode,
          };
          this.featuresCsv = v.features.join(', ');
          this.loadHubs(v.island.id);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.detail || 'Could not load vehicle for editing.');
          this.loading.set(false);
        },
      });
    }
  }

  onIslandChange(): void {
    this.form.pickupLocationPublicId = '';
    this.form.returnLocationPublicId = '';
    this.loadHubs(this.form.islandId);
  }

  syncReturnHub(): void {
    if (!this.form.allowDifferentReturnSameIsland) {
      this.form.returnLocationPublicId = this.form.pickupLocationPublicId;
    }
  }

  private loadHubs(islandId: number): void {
    if (!islandId) {
      this.hubs.set([]);
      return;
    }
    this.catalogService.getHubs(islandId).subscribe({ next: (h) => this.hubs.set(h) });
  }

  save(): void {
    this.error.set(null);
    if (!this.form.islandId || !this.form.categoryId) {
      this.error.set('Please select both an operating island and a vehicle category.');
      return;
    }

    const features = this.featuresCsv
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    const payload: SaveVehicleRequest = {
      islandId: this.form.islandId,
      categoryId: this.form.categoryId,
      pickupLocationPublicId: this.form.pickupLocationPublicId || null,
      returnLocationPublicId: this.form.allowDifferentReturnSameIsland
        ? this.form.returnLocationPublicId || null
        : this.form.pickupLocationPublicId || null,
      allowDifferentReturnSameIsland: this.form.allowDifferentReturnSameIsland,
      title: this.form.title.trim(),
      make: this.form.make.trim(),
      model: this.form.model.trim(),
      modelYear: Number(this.form.modelYear),
      description: this.form.description.trim() || null,
      registrationNumber: this.form.registrationNumber.trim().toUpperCase(),
      color: this.form.color.trim() || null,
      transmissionType: this.form.transmissionType,
      fuelType: this.form.fuelType,
      seatingCapacity: Number(this.form.seatingCapacity),
      mileageKmpl: this.form.mileageKmpl != null ? Number(this.form.mileageKmpl) : null,
      engineCapacityCc: this.form.engineCapacityCc != null ? Number(this.form.engineCapacityCc) : null,
      vehicleCondition: this.form.vehicleCondition,
      rentalInstructions: this.form.rentalInstructions.trim() || null,
      dailyRate: Number(this.form.dailyRate),
      securityDeposit: Number(this.form.securityDeposit || 0),
      turnaroundBufferMinutes: Number(this.form.turnaroundBufferMinutes || 60),
      bookingMode: this.form.bookingMode,
      features,
    };

    this.saving.set(true);
    const id = this.vehiclePublicId();
    const obs = id
      ? this.partnerService.updateVehicle(id, payload)
      : this.partnerService.createVehicle(payload);

    obs.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.router.navigate(['/partner/fleet', saved.publicId]);
      },
      error: (err) => {
        this.saving.set(false);
        const fieldErrors = err?.error?.errors ? Object.values(err.error.errors).join('; ') : '';
        this.error.set(fieldErrors || err?.error?.detail || 'Failed to save vehicle.');
      },
    });
  }
}

