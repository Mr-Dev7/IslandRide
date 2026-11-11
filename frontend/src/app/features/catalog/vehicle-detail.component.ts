import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/services/catalog.service';
import {
  PublicVehicleDetailDto,
  VehicleAvailabilityCalendarDto,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-vehicle-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="ir-container">
        <a routerLink="/search" class="link-btn back-link">← Back to Catalog</a>

        @if (loading()) {
          <div class="ir-card pad">Loading vehicle details...</div>
        } @else if (error()) {
          <div class="ir-alert ir-alert-error">{{ error() }}</div>
        } @else if (vehicle(); as v) {
          <div class="detail-grid">
            <div class="left-col">
              <div class="ir-card gallery-card">
                <div class="hero-img">
                  @if (activeImageUrl()) {
                    <img [src]="activeImageUrl()" [alt]="v.title" />
                  } @else {
                    <div class="media-placeholder">{{ v.category.name }} · Photo pending</div>
                  }
                </div>
                @if (v.images.length > 1) {
                  <div class="thumbs">
                    @for (img of v.images; track img.publicId) {
                      <button type="button" class="thumb-btn" [class.active]="activeImageUrl() === img.imageUrl" (click)="selectedImageUrl.set(img.imageUrl)">
                        <img [src]="img.imageUrl" [alt]="v.title" />
                      </button>
                    }
                  </div>
                }
              </div>

              <section class="ir-card pad">
                <div class="badge-row">
                  <span class="badge">{{ v.category.name }}</span>
                  <span class="badge">{{ v.island.displayAlias || v.island.name }}</span>
                  @if (v.verifiedPartner) {
                    <span class="badge badge-verified">Verified Partner: {{ v.partnerTradeName }}</span>
                  }
                </div>
                <h1>{{ v.title }}</h1>
                <p class="muted">{{ v.make }} {{ v.model }} · {{ v.modelYear }} @if (v.color) { · {{ v.color }} }</p>
                @if (v.description) {
                  <p class="desc">{{ v.description }}</p>
                }

                <h3>Specifications</h3>
                <div class="spec-grid">
                  <div><span class="muted">Transmission</span><strong>{{ v.transmissionType }}</strong></div>
                  <div><span class="muted">Fuel Type</span><strong>{{ v.fuelType }}</strong></div>
                  <div><span class="muted">Seats</span><strong>{{ v.seatingCapacity }}</strong></div>
                  <div><span class="muted">Condition</span><strong>{{ v.vehicleCondition }}</strong></div>
                  @if (v.mileageKmpl) {
                    <div><span class="muted">Mileage</span><strong>{{ v.mileageKmpl }} km/l</strong></div>
                  }
                  @if (v.engineCapacityCc) {
                    <div><span class="muted">Engine</span><strong>{{ v.engineCapacityCc }} cc</strong></div>
                  }
                  <div><span class="muted">Booking Mode</span><strong>{{ v.bookingMode }}</strong></div>
                  <div><span class="muted">Turnaround</span><strong>{{ v.turnaroundBufferMinutes }} mins</strong></div>
                </div>

                @if (v.features.length > 0) {
                  <h3>Included Features</h3>
                  <div class="feature-tags">
                    @for (feat of v.features; track feat) {
                      <span class="badge">{{ feat }}</span>
                    }
                  </div>
                }

                <h3>Pickup, Return & Rental Terms</h3>
                <ul class="terms-list">
                  <li><strong>Pickup Hub:</strong> {{ v.pickupLocation ? v.pickupLocation.name + ' (' + v.pickupLocation.address + ')' : v.island.name }}</li>
                  <li>
                    <strong>Return Policy:</strong>
                    {{ v.allowDifferentReturnSameIsland
                      ? ('Same-island flexible return' + (v.returnLocation ? ' (' + v.returnLocation.name + ')' : ''))
                      : ('Same hub return only · Inter-island transfers not allowed') }}
                  </li>
                  @if (v.rentalInstructions) {
                    <li><strong>Partner Instructions:</strong> {{ v.rentalInstructions }}</li>
                  }
                </ul>
              </section>
            </div>

            <aside class="right-col">
              <div class="ir-card pad pricing-card">
                <div class="rate-head">
                  <strong class="price">₹{{ v.pricingPlan.dailyRate | number: '1.0-0' }}</strong>
                  <span class="muted">/ day ({{ v.pricingPlan.currency }})</span>
                </div>
                <div class="muted small">Taxes: +{{ v.pricingPlan.gstPercentage }}% GST configurable rate</div>

                <dl class="pricing-dl">
                  <div><dt>Security Deposit</dt><dd>₹{{ v.pricingPlan.securityDeposit | number: '1.0-0' }}</dd></div>
                  @if (v.pricingPlan.hourlyRate) {
                    <div><dt>Hourly Rate</dt><dd>₹{{ v.pricingPlan.hourlyRate | number: '1.0-0' }}/hr</dd></div>
                  }
                  @if (v.pricingPlan.weeklyRate) {
                    <div><dt>Weekly Rate</dt><dd>₹{{ v.pricingPlan.weeklyRate | number: '1.0-0' }}/wk</dd></div>
                  }
                  <div><dt>Duration Limits</dt><dd>{{ v.pricingPlan.minRentalHours }} hrs – {{ v.pricingPlan.maxRentalDays }} days</dd></div>
                  @if (v.pricingPlan.includedKmPerDay != null) {
                    <div><dt>Included Distance</dt><dd>{{ v.pricingPlan.includedKmPerDay }} km/day</dd></div>
                  }
                  @if (v.pricingPlan.extraKmCharge != null) {
                    <div><dt>Extra KM Fee</dt><dd>₹{{ v.pricingPlan.extraKmCharge }}/km</dd></div>
                  }
                  @if (v.pricingPlan.extraHourCharge != null) {
                    <div><dt>Extra Hour Fee</dt><dd>₹{{ v.pricingPlan.extraHourCharge }}/hr</dd></div>
                  }
                  @if (v.pricingPlan.cleaningFee > 0) {
                    <div><dt>Cleaning Fee</dt><dd>₹{{ v.pricingPlan.cleaningFee }}</dd></div>
                  }
                  @if (v.pricingPlan.deliveryFee > 0) {
                    <div><dt>Delivery Fee</dt><dd>₹{{ v.pricingPlan.deliveryFee }}</dd></div>
                  }
                  <div><dt>Late Return Grace</dt><dd>{{ v.pricingPlan.lateReturnGraceMinutes }} mins</dd></div>
                </dl>

                @if (v.pricingRules.length > 0) {
                  <h4>Pricing Rules</h4>
                  @for (rule of v.pricingRules; track rule.id) {
                    <div class="rule-row">
                      <span>{{ rule.ruleName }} ({{ rule.minDays }}+ days)</span>
                      <strong>{{ rule.adjustmentValue }}{{ rule.adjustmentType === 'PERCENTAGE' ? '%' : '₹' }}</strong>
                    </div>
                  }
                }
              </div>

              <div class="ir-card pad">
                <h3>Check Availability</h3>
                <div class="field"><label for="chkStart">Start</label><input id="chkStart" type="datetime-local" [(ngModel)]="checkStart" /></div>
                <div class="field"><label for="chkEnd">End</label><input id="chkEnd" type="datetime-local" [(ngModel)]="checkEnd" /></div>
                <button type="button" class="ir-btn ir-btn-primary full-width" (click)="checkWindowAvailability()">
                  Check Availability Window
                </button>

                @if (availabilityError()) {
                  <div class="ir-alert ir-alert-error mt-sm">{{ availabilityError() }}</div>
                }
                @if (calendar(); as cal) {
                  <div class="avail-banner" [class.ok]="cal.availableForRequestedWindow" [class.busy]="!cal.availableForRequestedWindow">
                    @if (cal.availableForRequestedWindow) {
                      <strong>Available for your selected dates!</strong>
                    } @else {
                      <strong>Not available: {{ cal.conflictReason || 'Scheduled conflict in timeframe.' }}</strong>
                    }
                  </div>
                  @if (cal.unavailableSlots.length > 0) {
                    <h4 class="mt-sm">Blocked / Reserved Slots</h4>
                    <ul class="slot-list">
                      @for (slot of cal.unavailableSlots; track slot.startTime + slot.endTime) {
                        <li>
                          <span class="badge">{{ slot.displayLabel }}</span>
                          <span class="small muted">{{ slot.startTime | date: 'mediumDate' }} → {{ slot.endTime | date: 'mediumDate' }}</span>
                        </li>
                      }
                    </ul>
                  } @else {
                    <p class="muted small mt-sm">No upcoming unavailable blocks in this window.</p>
                  }
                }
                <p class="muted small mt-sm">Live availability lookup only. Booking & checkout will open in Phase 6.</p>
              </div>
            </aside>
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .back-link { display: inline-block; margin-bottom: .75rem; }
      .detail-grid { display: grid; grid-template-columns: 1.55fr 1fr; gap: 1.25rem; align-items: start; }
      .left-col, .right-col { display: flex; flex-direction: column; gap: 1rem; }
      .gallery-card { padding: .75rem; }
      .hero-img { height: 320px; border-radius: 10px; overflow: hidden; background: #e2e8f0; }
      .hero-img img, .thumb-btn img { width: 100%; height: 100%; object-fit: cover; display: block; }
      .media-placeholder { height: 100%; display: flex; align-items: center; justify-content: center; font-weight: 600; color: #475569; }
      .thumbs { display: flex; gap: .5rem; margin-top: .65rem; overflow-x: auto; }
      .thumb-btn { border: 2px solid transparent; padding: 0; border-radius: 6px; overflow: hidden; width: 72px; height: 50px; background: none; }
      .thumb-btn.active { border-color: var(--ir-primary); }
      .badge-row, .feature-tags, .slot-list { display: flex; flex-wrap: wrap; gap: .45rem; margin-bottom: .6rem; }
      .spec-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(145px, 1fr)); gap: .7rem; margin: .75rem 0 1rem; }
      .spec-grid div { background: #f8fafc; padding: .6rem; border-radius: 8px; display: flex; flex-direction: column; font-size: .85rem; }
      .terms-list { margin-top: .5rem; padding-left: 1.1rem; display: flex; flex-direction: column; gap: .35rem; }
      .price { font-size: 1.55rem; color: var(--ir-primary); }
      .pricing-dl { margin: .8rem 0; display: flex; flex-direction: column; gap: .35rem; font-size: .9rem; }
      .pricing-dl div, .slot-list li { display: flex; justify-content: space-between; width: 100%; }
      .pricing-dl div { border-bottom: 1px dashed var(--ir-border); padding-bottom: .25rem; }
      .pricing-dl dd { margin: 0; font-weight: 600; }
      .rule-row { display: flex; justify-content: space-between; font-size: .85rem; padding: .35rem .5rem; background: #f1f5f9; border-radius: 6px; margin-bottom: .35rem; }
      .avail-banner { padding: .65rem; border-radius: 6px; margin-top: .65rem; font-size: .88rem; }
      .avail-banner.ok { background: #dcfce7; color: #166534; }
      .avail-banner.busy { background: #fef2f2; color: #991b1b; }
      .slot-list { list-style: none; margin-top: .45rem; font-size: .85rem; }
      @media (max-width: 900px) { .detail-grid { grid-template-columns: 1fr; } }
    `,
  ],
})
export class VehicleDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogService = inject(CatalogService);

  vehicle = signal<PublicVehicleDetailDto | null>(null);
  calendar = signal<VehicleAvailabilityCalendarDto | null>(null);
  selectedImageUrl = signal<string | null>(null);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  availabilityError = signal<string | null>(null);

  checkStart = '';
  checkEnd = '';
  private vehiclePublicId = '';

  ngOnInit(): void {
    this.vehiclePublicId = this.route.snapshot.paramMap.get('publicId') || '';
    if (!this.vehiclePublicId) {
      this.error.set('Missing vehicle identifier.');
      this.loading.set(false);
      return;
    }

    this.catalogService.getVehicleDetail(this.vehiclePublicId).subscribe({
      next: (data) => {
        this.vehicle.set(data);
        if (data.images.length > 0) {
          this.selectedImageUrl.set(data.images[0].imageUrl);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || 'Vehicle not found or currently unavailable.');
        this.loading.set(false);
      },
    });

    this.catalogService.getVehicleAvailability(this.vehiclePublicId).subscribe({
      next: (cal) => this.calendar.set(cal),
    });
  }

  activeImageUrl(): string | null {
    return this.selectedImageUrl() || this.vehicle()?.images[0]?.imageUrl || null;
  }

  checkWindowAvailability(): void {
    this.availabilityError.set(null);
    if (!this.checkStart || !this.checkEnd) {
      this.availabilityError.set('Please select both Start and End timestamps.');
      return;
    }
    const fromIso = new Date(this.checkStart).toISOString();
    const toIso = new Date(this.checkEnd).toISOString();
    this.catalogService.getVehicleAvailability(this.vehiclePublicId, fromIso, toIso).subscribe({
      next: (cal) => this.calendar.set(cal),
      error: (err) =>
        this.availabilityError.set(err?.error?.detail || 'Invalid availability window.'),
    });
  }
}
