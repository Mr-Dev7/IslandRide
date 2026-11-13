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
          <a [routerLink]="isEditMode() ? ['/partner/fleet', vehiclePublicId()] : ['/partner/fleet']" class="link-btn">
            &larr; Back to Fleet
          </a>
          <h1>{{ isEditMode() ? 'Edit Vehicle Specifications' : 'Add New Rental Vehicle' }}</h1>
          <p class="muted">Complete the 3-step listing wizard to publish inventory to the Andaman marketplace.</p>
        </div>
      </div>

      @if (isEditMode() && currentStatus() === 'ACTIVE') {
        <div class="ir-alert ir-alert-warn">
          <strong>Re-Approval Notice:</strong> This listing is currently <strong>ACTIVE</strong>. Changing core specs (make, model, year, category, registration number, island, or hub) will automatically return it to <strong>PENDING_REVIEW</strong> for operations verification.
        </div>
      }

      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      @if (loading()) {
        <div class="ir-card pad muted text-center py-8">Loading vehicle form...</div>
      } @else {
        <!-- Progressive 3-Step Wizard Navigation -->
        <div class="wizard-steps-bar">
          <button
            type="button"
            class="wizard-step"
            [class.active]="currentStep() === 1"
            [class.completed]="currentStep() > 1"
            (click)="goToStep(1)"
          >
            <span class="step-badge">1</span>
            <div class="step-info">
              <span class="step-title">Vehicle & Hub</span>
              <span class="step-sub">Model, specs, island</span>
            </div>
          </button>

          <div class="wizard-connector" [class.active]="currentStep() >= 2"></div>

          <button
            type="button"
            class="wizard-step"
            [class.active]="currentStep() === 2"
            [class.completed]="currentStep() > 2"
            (click)="goToStep(2)"
          >
            <span class="step-badge">2</span>
            <div class="step-info">
              <span class="step-title">Pricing & Mode</span>
              <span class="step-sub">Daily rates, deposits</span>
            </div>
          </button>

          <div class="wizard-connector" [class.active]="currentStep() === 3"></div>

          <button
            type="button"
            class="wizard-step"
            [class.active]="currentStep() === 3"
            (click)="goToStep(3)"
          >
            <span class="step-badge">3</span>
            <div class="step-info">
              <span class="step-title">Terms & Review</span>
              <span class="step-sub">Instructions, finalize</span>
            </div>
          </button>
        </div>

        <form class="ir-card pad wizard-card" (ngSubmit)="save()">
          <!-- STEP 1: Vehicle & Hub Information -->
          @if (currentStep() === 1) {
            <div class="step-section">
              <div class="step-header">
                <h3>1. Operating Location & Identity</h3>
                <p class="muted small">Select the island deployment base and primary vehicle specifications.</p>
              </div>

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
                <div class="cat-hint">
                  <strong>{{ cat.name }}</strong> &bull; Recommended attributes: {{ cat.applicableAttributes.join(', ') || 'standard attributes' }}.
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
                <span>Allow returning vehicle at a different hub on the same island</span>
              </label>

              <div class="divider-line"></div>

              <div class="step-header">
                <h3>Technical Details & Registration</h3>
              </div>

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
                <div class="field"><label for="v-make">Make *</label><input id="v-make" type="text" [(ngModel)]="form.make" name="make" required placeholder="e.g. Honda" /></div>
                <div class="field"><label for="v-model">Model *</label><input id="v-model" type="text" [(ngModel)]="form.model" name="model" required placeholder="e.g. Activa 6G" /></div>
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
                <div class="field"><label for="v-color">Color</label><input id="v-color" type="text" [(ngModel)]="form.color" name="color" placeholder="e.g. White" /></div>
                <div class="field"><label for="v-mileage">Mileage (km/l)</label><input id="v-mileage" type="number" step="0.1" [(ngModel)]="form.mileageKmpl" name="mileageKmpl" /></div>
                <div class="field"><label for="v-cc">Engine Capacity (cc)</label><input id="v-cc" type="number" [(ngModel)]="form.engineCapacityCc" name="engineCapacityCc" /></div>
              </div>

              <div class="field-row">
                <div class="field">
                  <label for="v-cond">Vehicle Condition</label>
                  <select id="v-cond" [(ngModel)]="form.vehicleCondition" name="vehicleCondition">
                    <option value="EXCELLENT">Excellent (Like New)</option>
                    <option value="GOOD">Good (Maintained)</option>
                    <option value="FAIR">Fair (Functional)</option>
                  </select>
                </div>
              </div>

              <div class="wizard-nav-actions">
                <span></span>
                <button type="button" class="ir-btn ir-btn-primary" (click)="validateAndProceedToStep(2)">
                  Next: Pricing & Mode &rarr;
                </button>
              </div>
            </div>
          }

          <!-- STEP 2: Pricing & Availability Mode -->
          @if (currentStep() === 2) {
            <div class="step-section">
              <div class="step-header">
                <h3>2. Pricing & Operational Modes</h3>
                <p class="muted small">Set transparent rental rates, fiduciary security deposit, and booking confirmation rules.</p>
              </div>

              <div class="field-row">
                <div class="field">
                  <label for="v-rate">Base Daily Rate (INR ₹) *</label>
                  <input id="v-rate" type="number" min="50" step="0.01" [(ngModel)]="form.dailyRate" name="dailyRate" required />
                  <span class="field-hint">Standard 24-hour rental price charged to tourists</span>
                </div>
                <div class="field">
                  <label for="v-dep">Security Deposit (INR ₹)</label>
                  <input id="v-dep" type="number" min="0" step="0.01" [(ngModel)]="form.securityDeposit" name="securityDeposit" />
                  <span class="field-hint">Held safely in platform escrow, returned post-inspection</span>
                </div>
              </div>

              <div class="field-row">
                <div class="field">
                  <label for="v-mode">Confirmation Mode</label>
                  <select id="v-mode" [(ngModel)]="form.bookingMode" name="bookingMode">
                    <option value="INSTANT">Instant Booking (Auto-confirms on payment)</option>
                    <option value="APPROVAL">Partner Approval Required (You review first)</option>
                  </select>
                  <span class="field-hint">Instant bookings receive higher conversion on IslandRide</span>
                </div>
                <div class="field">
                  <label for="v-buf">Turnaround Cleaning Buffer (mins)</label>
                  <input id="v-buf" type="number" min="0" max="720" [(ngModel)]="form.turnaroundBufferMinutes" name="turnaroundBufferMinutes" />
                  <span class="field-hint">Buffer time between consecutive bookings for cleaning/service</span>
                </div>
              </div>

              <div class="wizard-nav-actions">
                <button type="button" class="ir-btn ir-btn-outline" (click)="currentStep.set(1)">
                  &larr; Back: Vehicle Info
                </button>
                <button type="button" class="ir-btn ir-btn-primary" (click)="validateAndProceedToStep(3)">
                  Next: Terms & Review &rarr;
                </button>
              </div>
            </div>
          }

          <!-- STEP 3: Terms, Features & Final Review -->
          @if (currentStep() === 3) {
            <div class="step-section">
              <div class="step-header">
                <h3>3. Features, Instructions & Review</h3>
                <p class="muted small">Add amenities, pickup instructions, and review your configuration before submission.</p>
              </div>

              <div class="field">
                <label for="v-features">Included Features (comma separated)</label>
                <input id="v-features" type="text" [(ngModel)]="featuresCsv" name="featuresCsv" placeholder="Helmet Included, USB Charger, Phone Mount, First Aid Kit" />
                <span class="field-hint">Visible as highlight tags on the tourist catalog</span>
              </div>

              <div class="field">
                <label for="v-desc">Listing Description</label>
                <textarea id="v-desc" rows="3" [(ngModel)]="form.description" name="description" placeholder="Describe the vehicle condition, handling, and island tour recommendations."></textarea>
              </div>

              <div class="field">
                <label for="v-instr">Pickup / Rental Instructions</label>
                <textarea id="v-instr" rows="3" [(ngModel)]="form.rentalInstructions" name="rentalInstructions" placeholder="e.g. Carry original driving licence and Aadhaar/Passport at pickup. Contact +91... upon ferry arrival."></textarea>
              </div>

              <!-- Live Configuration Summary Card -->
              <div class="review-box">
                <h4>Listing Summary Overview</h4>
                <div class="review-grid">
                  <div><span class="muted small">Vehicle:</span> <strong>{{ form.title || (form.make + ' ' + form.model) }} ({{ form.modelYear }})</strong></div>
                  <div><span class="muted small">Registration:</span> <strong>{{ form.registrationNumber || 'Not set' }}</strong></div>
                  <div><span class="muted small">Island:</span> <strong>{{ getIslandName(form.islandId) }}</strong></div>
                  <div><span class="muted small">Daily Rate:</span> <strong class="price-text">₹{{ form.dailyRate }}/day</strong></div>
                  <div><span class="muted small">Security Deposit:</span> <strong>₹{{ form.securityDeposit || 0 }}</strong></div>
                  <div><span class="muted small">Mode:</span> <strong>{{ form.bookingMode }}</strong></div>
                </div>
              </div>

              <div class="wizard-nav-actions">
                <button type="button" class="ir-btn ir-btn-outline" (click)="currentStep.set(2)">
                  &larr; Back: Pricing & Mode
                </button>
                <div class="submit-group">
                  <a [routerLink]="isEditMode() ? ['/partner/fleet', vehiclePublicId()] : ['/partner/fleet']" class="ir-btn ir-btn-outline">Cancel</a>
                  <button type="submit" class="ir-btn ir-btn-primary" [disabled]="saving()">
                    {{ saving() ? 'Saving...' : (isEditMode() ? 'Save Changes' : 'Create Vehicle Draft') }}
                  </button>
                </div>
              </div>
            </div>
          }
        </form>
      }
    </div>
  `,
  styles: [`
    .ir-page { max-width: 900px; margin: 0 auto; padding: 1.5rem 1rem; }
    .page-head { margin-bottom: 1.5rem; }
    .page-head h1 { font-size: 1.625rem; font-weight: 700; color: var(--ir-navy); margin: 0.25rem 0; }
    .link-btn { font-size: 0.875rem; color: var(--ir-teal); font-weight: 600; text-decoration: none; }
    .link-btn:hover { text-decoration: underline; }

    /* Wizard Step Indicator */
    .wizard-steps-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.5rem;
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      padding: 0.75rem 1.25rem;
      box-shadow: var(--ir-shadow-sm);
    }
    .wizard-step {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      background: none;
      border: none;
      cursor: pointer;
      padding: 0.35rem 0.5rem;
      border-radius: var(--ir-radius-sm);
      text-align: left;
      transition: all var(--ir-transition);
    }
    .wizard-step:hover {
      background: var(--ir-sea-mist);
    }
    .step-badge {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--ir-border);
      color: var(--ir-charcoal);
      font-weight: 700;
      font-size: 0.875rem;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all var(--ir-transition);
      flex-shrink: 0;
    }
    .wizard-step.active .step-badge {
      background: var(--ir-teal);
      color: #ffffff;
      box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.2);
    }
    .wizard-step.completed .step-badge {
      background: var(--ir-navy);
      color: #ffffff;
    }
    .step-info {
      display: flex;
      flex-direction: column;
    }
    .step-title {
      font-size: 0.875rem;
      font-weight: 700;
      color: var(--ir-charcoal);
    }
    .wizard-step.active .step-title {
      color: var(--ir-navy);
    }
    .step-sub {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }
    .wizard-connector {
      flex: 1;
      height: 2px;
      background: var(--ir-border);
      margin: 0 0.75rem;
      transition: background var(--ir-transition);
    }
    .wizard-connector.active {
      background: var(--ir-teal);
    }

    @media (max-width: 650px) {
      .wizard-steps-bar {
        flex-direction: column;
        align-items: stretch;
        gap: 0.5rem;
      }
      .wizard-connector { display: none; }
    }

    /* Wizard Card Content */
    .wizard-card {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      box-shadow: var(--ir-shadow-sm);
    }
    .step-header {
      margin-bottom: 1.25rem;
    }
    .step-header h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.25rem 0;
    }
    .cat-hint {
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-sm);
      padding: 0.625rem 0.875rem;
      font-size: 0.8125rem;
      color: var(--ir-navy);
      margin-bottom: 1rem;
    }
    .field-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1rem;
    }
    .field-row-3 {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      gap: 1rem;
      margin-bottom: 1rem;
    }
    @media (max-width: 650px) {
      .field-row, .field-row-3 {
        grid-template-columns: 1fr;
      }
    }
    .field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .field label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--ir-charcoal);
    }
    .field input, .field select, .field textarea {
      padding: 0.55rem 0.75rem;
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-sm);
      font-size: 0.875rem;
      background: #ffffff;
      color: var(--ir-charcoal);
      width: 100%;
      box-sizing: border-box;
    }
    .field input:focus, .field select:focus, .field textarea:focus {
      outline: none;
      border-color: var(--ir-teal);
      box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.15);
    }
    .field-hint {
      font-size: 0.75rem;
      color: var(--ir-muted);
    }
    .check-line {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      color: var(--ir-charcoal);
      margin: 0.5rem 0 1rem;
      cursor: pointer;
    }
    .divider-line {
      border-top: 1px solid var(--ir-border);
      margin: 1.5rem 0;
    }

    /* Review Box */
    .review-box {
      background: var(--ir-sea-mist);
      border: 1px solid #c2e0d8;
      border-radius: var(--ir-radius-sm);
      padding: 1.25rem;
      margin: 1.25rem 0;
    }
    .review-box h4 {
      font-size: 0.9375rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.75rem 0;
    }
    .review-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 0.75rem;
      font-size: 0.875rem;
    }
    .price-text {
      color: var(--ir-navy);
      font-size: 1rem;
    }

    /* Navigation Actions */
    .wizard-nav-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 2rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--ir-border);
      gap: 1rem;
      flex-wrap: wrap;
    }
    .submit-group {
      display: flex;
      gap: 0.75rem;
    }
  `],
})
export class PartnerVehicleFormComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalogService = inject(CatalogService);
  private readonly partnerService = inject(PartnerInventoryService);

  currentStep = signal<1 | 2 | 3>(1);

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

  goToStep(step: 1 | 2 | 3): void {
    this.error.set(null);
    this.currentStep.set(step);
  }

  validateStep1(): boolean {
    if (!this.form.islandId || !this.form.categoryId) {
      this.error.set('Please select both an operating island and a vehicle category.');
      return false;
    }
    this.error.set(null);
    return true;
  }

  validateStep2(): boolean {
    if (this.form.dailyRate == null || this.form.dailyRate < 0) {
      this.error.set('Please specify a valid daily rental rate.');
      return false;
    }
    this.error.set(null);
    return true;
  }

  validateAndProceedToStep(step: 2 | 3): void {
    if (step === 2 && this.validateStep1()) {
      this.currentStep.set(2);
    } else if (step === 3 && this.validateStep2()) {
      this.currentStep.set(3);
    }
  }

  getIslandName(id: number): string {
    const isl = this.islands().find(i => i.id === id);
    return isl ? (isl.displayAlias || isl.name) : 'Not selected';
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
        const params = err?.error?.invalidParams;
        const fieldErrors = Array.isArray(params)
          ? params
              .map((p: { name?: string; reason?: string }) => (p.name ? `${p.name}: ${p.reason}` : p.reason))
              .filter(Boolean)
              .join('; ')
          : '';
        this.error.set(fieldErrors || err?.error?.detail || 'Failed to save vehicle.');
      },
    });
  }
}
