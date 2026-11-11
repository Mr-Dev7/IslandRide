import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { PartnerInventoryService } from '../../core/services/partner-inventory.service';
import {
  BlockType,
  PartnerVehicleDto,
  SaveVehiclePricingRequest,
  VehicleAvailabilityCalendarDto,
  VehicleDocumentDto,
  VehicleDocumentTypeDto,
  VehiclePricingOverviewDto,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-partner-vehicle-manage',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <a routerLink="/partner/fleet" class="link-btn">← Back to Fleet</a>

      @if (message()) {
        <div class="ir-alert ir-alert-success mt-sm">{{ message() }}</div>
      }
      @if (error()) {
        <div class="ir-alert ir-alert-error mt-sm">{{ error() }}</div>
      }

      @if (loading()) {
        <div class="ir-card pad mt-sm muted">Loading vehicle details...</div>
      } @else if (vehicle(); as v) {
        <div class="ir-card pad mt-sm mb-md">
          <div class="page-head">
            <div>
              <div class="actions-row">
                <span class="status-pill status-{{ v.status }}">{{ v.status }}</span>
                <span class="badge">{{ v.category.name }}</span>
                <span class="badge">{{ v.island.displayAlias || v.island.name }}</span>
                <code>{{ v.registrationNumber }}</code>
              </div>
              <h1 class="mt-sm">{{ v.title }}</h1>
              <p class="muted">{{ v.make }} {{ v.model }} · {{ v.modelYear }} · Daily Rate ₹{{ v.dailyRate | number:'1.0-0' }} · Deposit ₹{{ v.securityDeposit | number:'1.0-0' }}</p>
            </div>
            <div class="actions-row">
              @if (canWrite() && v.status !== 'ARCHIVED') {
                <a [routerLink]="['/partner/fleet', v.publicId, 'edit']" class="ir-btn ir-btn-outline ir-btn-sm">Edit Specs</a>
              }
              @if (canWrite() && (v.status === 'DRAFT' || v.status === 'REJECTED')) {
                <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" (click)="runWorkflow('submit')">Submit for Review</button>
              }
              @if (canWrite() && v.status === 'ACTIVE') {
                <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="runWorkflow('pause')">Pause</button>
              }
              @if (canWrite() && (v.status === 'PAUSED' || v.status === 'MAINTENANCE')) {
                <button type="button" class="ir-btn ir-btn-secondary ir-btn-sm" (click)="runWorkflow('resume')">Resume Active</button>
              }
              @if (canWrite() && (v.status === 'ACTIVE' || v.status === 'PAUSED')) {
                <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="runWorkflow('maintenance')">Mark Maintenance</button>
              }
              @if (canWrite() && v.status !== 'ARCHIVED' && v.status !== 'PENDING_REVIEW') {
                <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="runWorkflow('archive')">Archive</button>
              }
            </div>
          </div>

          @if (v.rejectionReason) {
            <div class="ir-alert ir-alert-error"><strong>Rejection Reason:</strong> {{ v.rejectionReason }}</div>
          }
          @if (v.suspensionReason) {
            <div class="ir-alert ir-alert-error"><strong>Suspension Reason:</strong> {{ v.suspensionReason }}</div>
          }
        </div>

        <div class="tabs">
          <button type="button" class="tab-btn" [class.active]="tab() === 'photos'" (click)="tab.set('photos')">Photos ({{ v.images.length }})</button>
          <button type="button" class="tab-btn" [class.active]="tab() === 'docs'" (click)="tab.set('docs')">Compliance Docs ({{ v.documents.length }})</button>
          <button type="button" class="tab-btn" [class.active]="tab() === 'pricing'" (click)="tab.set('pricing')">Pricing & Rules</button>
          <button type="button" class="tab-btn" [class.active]="tab() === 'calendar'" (click)="tab.set('calendar')">Availability Calendar</button>
          <button type="button" class="tab-btn" [class.active]="tab() === 'history'" (click)="tab.set('history')">Status History</button>
        </div>

        <!-- TAB 1: PHOTOS -->
        @if (tab() === 'photos') {
          <div class="ir-card pad">
            <h3>Vehicle Gallery</h3>
            <p class="muted small mb-md">Upload JPEG, PNG, or WebP photos (max 5 MB each, up to 10 images). Set primary photo and display order.</p>

            @if (canWrite() && v.status !== 'ARCHIVED') {
              <div class="upload-bar mb-md">
                <input type="file" multiple accept="image/jpeg,image/png,image/webp" (change)="onImagesSelected($event)" />
                <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" [disabled]="selectedImages.length === 0 || busy()" (click)="uploadImages()">Upload Selected</button>
              </div>
            }

            @if (v.images.length === 0) {
              <p class="muted">No photos uploaded yet.</p>
            } @else {
              <div class="img-grid">
                @for (img of v.images; track img.publicId; let i = $index) {
                  <div class="img-tile">
                    <div class="img-box">
                      @if (imagePreviews()[img.publicId]) {
                        <img [src]="imagePreviews()[img.publicId]" [alt]="img.originalFilename || v.title" />
                      } @else {
                        <span class="muted small">Loading preview...</span>
                      }
                      @if (img.primary) {
                        <span class="badge badge-verified prim-tag">Primary</span>
                      }
                    </div>
                    <div class="muted small">{{ img.originalFilename || 'image' }}</div>
                    @if (canWrite() && v.status !== 'ARCHIVED') {
                      <div class="actions-row mt-sm">
                        @if (!img.primary) {
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="setPrimaryImage(img.publicId)">Make Primary</button>
                        }
                        @if (i > 0) {
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="moveImage(i, -1)">←</button>
                        }
                        @if (i + 1 < v.images.length) {
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="moveImage(i, 1)">→</button>
                        }
                        <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="deleteImage(img.publicId)">Delete</button>
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </div>
        }

        <!-- TAB 2: DOCUMENTS -->
        @if (tab() === 'docs') {
          <div class="ir-card pad">
            <h3>Compliance & KYC Documents</h3>
            <p class="muted small mb-md">Documents remain private (owner/manager/ops-admin access only). Ensure RC, Insurance, Permit & PUC are verified and unexpired.</p>

            @if (canWrite() && v.status !== 'ARCHIVED') {
              <form class="doc-upload mb-md" (ngSubmit)="uploadDocument()">
                <div class="field-row-3">
                  <div class="field">
                    <label for="docType">Document Type *</label>
                    <select id="docType" [(ngModel)]="docForm.documentTypeCode" name="docType" required>
                      @for (dt of docTypes(); track dt.code) {
                        <option [value]="dt.code">{{ dt.name }} ({{ dt.applicableVehicleTypes }})</option>
                      }
                    </select>
                  </div>
                  <div class="field">
                    <label for="docNum">Document Number</label>
                    <input id="docNum" type="text" [(ngModel)]="docForm.documentNumber" name="docNum" placeholder="RC / Policy no." />
                  </div>
                  <div class="field">
                    <label for="docFile">File (PDF/JPEG/PNG/WebP) *</label>
                    <input id="docFile" type="file" accept=".pdf,image/jpeg,image/png,image/webp" (change)="onDocFileSelected($event)" />
                  </div>
                </div>
                <div class="field-row">
                  <div class="field"><label for="docIssue">Issue Date</label><input id="docIssue" type="date" [(ngModel)]="docForm.issueDate" name="docIssue" /></div>
                  <div class="field"><label for="docExpiry">Expiry Date</label><input id="docExpiry" type="date" [(ngModel)]="docForm.expiryDate" name="docExpiry" /></div>
                </div>
                <button type="submit" class="ir-btn ir-btn-primary ir-btn-sm" [disabled]="!docFile || busy()">Upload Document</button>
              </form>
            }

            @if (v.documents.length === 0) {
              <p class="muted">No compliance documents uploaded yet.</p>
            } @else {
              <div class="ir-table-wrap">
                <table class="ir-table">
                  <thead>
                    <tr>
                      <th>Document Type</th>
                      <th>Doc No.</th>
                      <th>Dates</th>
                      <th>Verification</th>
                      <th>File</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (d of v.documents; track d.publicId) {
                      <tr>
                        <td><strong>{{ d.documentTypeName }}</strong><div class="muted small">{{ d.documentTypeCode }}</div></td>
                        <td>{{ d.documentNumber || '—' }}</td>
                        <td class="small">
                          <div>Issued: {{ d.issueDate || '—' }}</div>
                          <div>Expires: {{ d.expiryDate || 'N/A' }}
                            @if (d.expired) { <span class="badge status-EXPIRED">EXPIRED</span> }
                            @else if (d.expiringSoon) { <span class="badge status-PENDING_REVIEW">Expiring soon</span> }
                          </div>
                        </td>
                        <td>
                          <span class="status-pill status-{{ d.verificationStatus }}">{{ d.verificationStatus }}</span>
                          @if (d.rejectionReason) {
                            <div class="small muted">{{ d.rejectionReason }}</div>
                          }
                        </td>
                        <td>
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="downloadDocument(d)">
                            Download ({{ d.originalFilename || 'file' }})
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }

        <!-- TAB 3: PRICING & RULES -->
        @if (tab() === 'pricing') {
          <div class="ir-card pad mb-md">
            <div class="page-head">
              <div>
                <h3>Active Pricing Plan</h3>
                <p class="muted small">Platform Currency: <strong>{{ pricingForm.currency }}</strong> · Applicable GST: <strong>{{ pricingOverview()?.activePlan?.gstPercentage ?? 5 }}%</strong></p>
              </div>
            </div>

            <form (ngSubmit)="savePricing()">
              <div class="field-row-3">
                <div class="field"><label>Daily Rate (₹) *</label><input type="number" step="0.01" min="1" [(ngModel)]="pricingForm.dailyRate" name="pDaily" required /></div>
                <div class="field"><label>Hourly Rate (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.hourlyRate" name="pHourly" /></div>
                <div class="field"><label>Weekly Rate (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.weeklyRate" name="pWeekly" /></div>
              </div>
              <div class="field-row-3">
                <div class="field"><label>Security Deposit (₹) *</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.securityDeposit" name="pDep" required /></div>
                <div class="field"><label>Min Rental Hours</label><input type="number" min="1" [(ngModel)]="pricingForm.minRentalHours" name="pMinH" /></div>
                <div class="field"><label>Max Rental Days</label><input type="number" min="1" [(ngModel)]="pricingForm.maxRentalDays" name="pMaxD" /></div>
              </div>
              <div class="field-row-3">
                <div class="field"><label>Included km / day</label><input type="number" min="0" [(ngModel)]="pricingForm.includedKmPerDay" name="pKm" /></div>
                <div class="field"><label>Extra km charge (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.extraKmCharge" name="pExKm" /></div>
                <div class="field"><label>Extra hour charge (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.extraHourCharge" name="pExHr" /></div>
              </div>
              <div class="field-row-3">
                <div class="field"><label>Cleaning Fee (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.cleaningFee" name="pClean" /></div>
                <div class="field"><label>Delivery Fee (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.deliveryFee" name="pDel" /></div>
                <div class="field"><label>Late Return Grace (mins)</label><input type="number" min="0" [(ngModel)]="pricingForm.lateReturnGraceMinutes" name="pGrace" /></div>
              </div>
              <div class="field-row">
                <div class="field"><label>Late Return Fee / Hour (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.lateReturnFeePerHour" name="pLateHr" /></div>
                <div class="field"><label>Late Return Flat Penalty (₹)</label><input type="number" step="0.01" min="0" [(ngModel)]="pricingForm.lateReturnFlatPenalty" name="pLateFlat" /></div>
              </div>
              <div class="field-row">
                <div class="field"><label>Plan Valid From</label><input type="datetime-local" [(ngModel)]="pricingValidFromLocal" name="pValidFrom" /></div>
                <div class="field"><label>Plan Valid To</label><input type="datetime-local" [(ngModel)]="pricingValidToLocal" name="pValidTo" /></div>
              </div>
              <div class="field"><label>Additional Charges Note</label><input type="text" [(ngModel)]="pricingForm.additionalChargesDescription" name="pAddDesc" /></div>

              @if (canWrite() && v.status !== 'ARCHIVED') {
                <button type="submit" class="ir-btn ir-btn-primary ir-btn-sm" [disabled]="busy()">Save Pricing Plan</button>
              }
            </form>
          </div>

          <div class="ir-card pad mb-md">
            <h3>Dynamic Pricing Rules</h3>
            @if (canWrite() && v.status !== 'ARCHIVED') {
              <form class="rule-row mb-md" (ngSubmit)="addRule()">
                <input type="text" [(ngModel)]="ruleForm.ruleName" name="rName" placeholder="e.g. 5-Day Island Discount" required />
                <select [(ngModel)]="ruleForm.ruleType" name="rType">
                  <option value="DURATION_DISCOUNT">Duration Discount</option>
                  <option value="WEEKEND_SURGE">Weekend Surge</option>
                  <option value="EARLY_BIRD">Early Bird</option>
                </select>
                <input type="number" min="1" [(ngModel)]="ruleForm.minDays" name="rDays" placeholder="Min days" required />
                <select [(ngModel)]="ruleForm.adjustmentType" name="rAdjType">
                  <option value="PERCENTAGE">Percentage (%)</option>
                  <option value="FIXED_AMOUNT">Fixed (₹)</option>
                </select>
                <input type="number" step="0.01" [(ngModel)]="ruleForm.adjustmentValue" name="rVal" placeholder="-10 or +200" required />
                <button type="submit" class="ir-btn ir-btn-outline ir-btn-sm" [disabled]="busy()">Add Rule</button>
              </form>
            }

            @if ((pricingOverview()?.rules ?? []).length === 0) {
              <p class="muted small">No pricing rules configured.</p>
            } @else {
              <table class="ir-table">
                <thead><tr><th>Rule</th><th>Type</th><th>Min Days</th><th>Adjustment</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  @for (r of pricingOverview()?.rules ?? []; track r.id) {
                    <tr>
                      <td>{{ r.ruleName }}</td>
                      <td>{{ r.ruleType }}</td>
                      <td>{{ r.minDays }}d+</td>
                      <td>{{ r.adjustmentValue }} {{ r.adjustmentType === 'PERCENTAGE' ? '%' : '₹' }}</td>
                      <td><span class="status-pill" [class.status-ACTIVE]="r.active" [class.status-ARCHIVED]="!r.active">{{ r.active ? 'ACTIVE' : 'INACTIVE' }}</span></td>
                      <td>
                        @if (canWrite() && r.active) {
                          <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="deactivateRule(r.id)">Deactivate</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>

          @if ((pricingOverview()?.history ?? []).length > 1) {
            <div class="ir-card pad">
              <h3>Pricing Plan History</h3>
              <table class="ir-table">
                <thead><tr><th>Daily</th><th>Weekly</th><th>Deposit</th><th>Active</th><th>Window</th></tr></thead>
                <tbody>
                  @for (hp of pricingOverview()?.history ?? []; track $index) {
                    <tr>
                      <td>₹{{ hp.dailyRate }}</td>
                      <td>{{ hp.weeklyRate != null ? ('₹' + hp.weeklyRate) : '—' }}</td>
                      <td>₹{{ hp.securityDeposit }}</td>
                      <td>{{ hp.active ? 'Yes' : 'Historical' }}</td>
                      <td class="small">{{ hp.validFrom || 'Immediate' }} → {{ hp.validTo || 'Open' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }

        <!-- TAB 4: AVAILABILITY CALENDAR -->
        @if (tab() === 'calendar') {
          <div class="ir-card pad mb-md">
            <h3>Vehicle Availability & Blocks</h3>
            <div class="field-row-3 mt-sm">
              <div class="field"><label>From</label><input type="datetime-local" [(ngModel)]="calFromLocal" /></div>
              <div class="field"><label>To</label><input type="datetime-local" [(ngModel)]="calToLocal" /></div>
              <div class="field" style="justify-content: flex-end;">
                <button type="button" class="ir-btn ir-btn-outline" (click)="loadAvailability()">Refresh Window</button>
              </div>
            </div>

            @if (calendar(); as cal) {
              <div class="ir-alert" [class.ir-alert-success]="cal.availableForRequestedWindow" [class.ir-alert-warn]="!cal.availableForRequestedWindow">
                {{ cal.availableForRequestedWindow ? 'Available for requested window' : ('Conflict: ' + (cal.conflictReason || 'Occupied during window')) }}
              </div>
            }

            @if (canWrite() && v.status !== 'ARCHIVED') {
              <form class="block-form mt-md mb-md" (ngSubmit)="createBlock()">
                <h4>Block Dates / Maintenance</h4>
                <div class="field-row-3 mt-sm">
                  <div class="field">
                    <label>Block Type</label>
                    <select [(ngModel)]="blockForm.blockType" name="bType">
                      <option value="MANUAL_BLOCK">Manual Block</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="DAMAGE_REPAIR">Damage Repair</option>
                      <option value="OPERATIONAL_RESTRICTION">Operational Restriction</option>
                    </select>
                  </div>
                  <div class="field"><label>Start *</label><input type="datetime-local" [(ngModel)]="blockStartLocal" name="bStart" required /></div>
                  <div class="field"><label>End *</label><input type="datetime-local" [(ngModel)]="blockEndLocal" name="bEnd" required /></div>
                </div>
                <div class="field-row">
                  <div class="field"><label>Reason *</label><input type="text" [(ngModel)]="blockForm.reason" name="bReason" required placeholder="e.g. Scheduled 10,000km servicing" /></div>
                  <div class="field"><label>Notes / Restriction Details</label><input type="text" [(ngModel)]="blockForm.notes" name="bNotes" /></div>
                </div>
                <button type="submit" class="ir-btn ir-btn-primary ir-btn-sm" [disabled]="busy()">Create Availability Block</button>
              </form>
            }

            @if ((calendar()?.detailedBlocks ?? []).length === 0) {
              <p class="muted small">No partner blocks in this window.</p>
            } @else {
              <table class="ir-table">
                <thead><tr><th>Type</th><th>From</th><th>To</th><th>Reason</th><th>Status</th><th></th></tr></thead>
                <tbody>
                  @for (b of calendar()?.detailedBlocks ?? []; track b.publicId) {
                    <tr>
                      <td>{{ b.blockType }}</td>
                      <td class="small">{{ b.startTime | date:'medium' }}</td>
                      <td class="small">{{ b.endTime | date:'medium' }}</td>
                      <td>{{ b.reason }}</td>
                      <td><span class="status-pill status-{{ b.status }}">{{ b.status }}</span></td>
                      <td>
                        @if (canWrite() && b.removable && b.status === 'ACTIVE') {
                          <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="removeBlock(b.publicId)">Unblock</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        }

        <!-- TAB 5: STATUS HISTORY -->
        @if (tab() === 'history') {
          <div class="ir-card pad">
            <h3>Status Transition History</h3>
            @if (v.statusHistory.length === 0) {
              <p class="muted small">No status transitions recorded.</p>
            } @else {
              <table class="ir-table">
                <thead><tr><th>Timestamp</th><th>From</th><th>To</th><th>Reason</th></tr></thead>
                <tbody>
                  @for (h of v.statusHistory; track h.id) {
                    <tr>
                      <td class="small">{{ h.createdAt | date:'medium' }}</td>
                      <td>{{ h.fromStatus || '—' }}</td>
                      <td><span class="status-pill status-{{ h.toStatus }}">{{ h.toStatus }}</span></td>
                      <td>{{ h.reason || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .upload-bar { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; }
    .img-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 0.9rem; }
    .img-tile { border: 1px solid var(--color-border); border-radius: 8px; padding: 0.65rem; background: #fff; }
    .img-box { height: 140px; background: #f1f5f9; border-radius: 6px; overflow: hidden; position: relative; display: flex; align-items: center; justify-content: center; margin-bottom: 0.45rem; }
    .img-box img { width: 100%; height: 100%; object-fit: cover; }
    .prim-tag { position: absolute; top: 6px; left: 6px; }
    .rule-row { display: grid; grid-template-columns: 2fr 1.3fr 0.7fr 1.2fr 0.9fr auto; gap: 0.5rem; }
    .rule-row input, .rule-row select { padding: 0.45rem 0.6rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.8125rem; }
    .block-form { border-top: 1px solid var(--color-border); padding-top: 1rem; }
    @media (max-width: 900px) { .rule-row { grid-template-columns: 1fr; } }
  `],
})
export class PartnerVehicleManageComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly catalogService = inject(CatalogService);
  private readonly partnerService = inject(PartnerInventoryService);

  vehicle = signal<PartnerVehicleDto | null>(null);
  pricingOverview = signal<VehiclePricingOverviewDto | null>(null);
  calendar = signal<VehicleAvailabilityCalendarDto | null>(null);
  docTypes = signal<VehicleDocumentTypeDto[]>([]);
  imagePreviews = signal<Record<string, string>>({});

  tab = signal<'photos' | 'docs' | 'pricing' | 'calendar' | 'history'>('photos');
  loading = signal<boolean>(true);
  busy = signal<boolean>(false);
  error = signal<string | null>(null);
  message = signal<string | null>(null);

  selectedImages: File[] = [];
  docFile: File | null = null;
  docForm = { documentTypeCode: 'RC_BOOK', documentNumber: '', issueDate: '', expiryDate: '' };

  pricingForm: SaveVehiclePricingRequest = {
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
    additionalChargesDescription: '',
    lateReturnFeePerHour: null,
    lateReturnFlatPenalty: null,
    lateReturnGraceMinutes: 30,
    active: true,
  };
  pricingValidFromLocal = '';
  pricingValidToLocal = '';

  ruleForm = {
    ruleName: '',
    ruleType: 'DURATION_DISCOUNT' as 'DURATION_DISCOUNT' | 'WEEKEND_SURGE' | 'EARLY_BIRD',
    minDays: 3,
    adjustmentType: 'PERCENTAGE' as 'PERCENTAGE' | 'FIXED_AMOUNT',
    adjustmentValue: -10,
  };

  calFromLocal = '';
  calToLocal = '';
  blockStartLocal = '';
  blockEndLocal = '';
  blockForm: { blockType: BlockType; reason: string; notes: string } = {
    blockType: 'MANUAL_BLOCK',
    reason: '',
    notes: '',
  };

  readonly canWrite = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    const hasRole = roles.includes('ROLE_PARTNER_OWNER') || roles.includes('ROLE_PARTNER_MANAGER');
    return hasRole && this.vehicle()?.businessVerificationStatus === 'APPROVED';
  });

  private vehiclePublicId = '';

  ngOnInit(): void {
    this.vehiclePublicId = this.route.snapshot.paramMap.get('publicId') || '';
    const now = new Date();
    const nextMonth = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
    this.calFromLocal = this.toLocalInput(now);
    this.calToLocal = this.toLocalInput(nextMonth);

    this.catalogService.getDocumentTypes().subscribe({
      next: (dts) => {
        this.docTypes.set(dts);
        if (dts.length > 0 && !this.docForm.documentTypeCode) {
          this.docForm.documentTypeCode = dts[0].code;
        }
      },
    });
    this.loadVehicle();
  }

  ngOnDestroy(): void {
    this.revokePreviews();
  }

  loadVehicle(): void {
    this.loading.set(true);
    this.partnerService.getVehicle(this.vehiclePublicId).subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.loading.set(false);
        this.loadImagePreviews(v);
        this.loadPricing();
        this.loadAvailability();
      },
      error: (err) => {
        this.error.set(err?.error?.detail || 'Failed to load vehicle details.');
        this.loading.set(false);
      },
    });
  }

  private loadImagePreviews(v: PartnerVehicleDto): void {
    this.revokePreviews();
    for (const img of v.images) {
      this.partnerService.downloadPrivateFile(img.imageUrl).subscribe({
        next: (blob) => {
          const objUrl = URL.createObjectURL(blob);
          this.imagePreviews.update((m) => ({ ...m, [img.publicId]: objUrl }));
        },
      });
    }
  }

  private revokePreviews(): void {
    const current = this.imagePreviews();
    Object.values(current).forEach((url) => URL.revokeObjectURL(url));
    this.imagePreviews.set({});
  }

  runWorkflow(action: 'submit' | 'pause' | 'resume' | 'maintenance' | 'archive'): void {
    this.error.set(null);
    this.message.set(null);
    const obs =
      action === 'submit'
        ? this.partnerService.submitForReview(this.vehiclePublicId)
        : action === 'pause'
          ? this.partnerService.pauseVehicle(this.vehiclePublicId)
          : action === 'resume'
            ? this.partnerService.resumeVehicle(this.vehiclePublicId)
            : action === 'maintenance'
              ? this.partnerService.markMaintenance(this.vehiclePublicId)
              : this.partnerService.archiveVehicle(this.vehiclePublicId);

    obs.subscribe({
      next: (updated) => {
        this.vehicle.set(updated);
        this.message.set(`Status updated to ${updated.status}.`);
      },
      error: (err) => this.error.set(err?.error?.detail || `Failed to execute ${action}.`),
    });
  }

  onImagesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedImages = input.files ? Array.from(input.files) : [];
  }

  uploadImages(): void {
    if (this.selectedImages.length === 0) return;
    this.busy.set(true);
    this.error.set(null);
    this.partnerService.uploadImages(this.vehiclePublicId, this.selectedImages).subscribe({
      next: () => {
        this.selectedImages = [];
        this.busy.set(false);
        this.message.set('Images uploaded successfully.');
        this.loadVehicle();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Image upload failed.');
      },
    });
  }

  setPrimaryImage(imagePublicId: string): void {
    this.partnerService.setPrimaryImage(this.vehiclePublicId, imagePublicId).subscribe({
      next: () => {
        this.message.set('Primary image updated.');
        this.loadVehicle();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Could not update primary image.'),
    });
  }

  moveImage(index: number, delta: number): void {
    const imgs = [...(this.vehicle()?.images ?? [])];
    const target = index + delta;
    if (target < 0 || target >= imgs.length) return;
    [imgs[index], imgs[target]] = [imgs[target], imgs[index]];
    this.partnerService
      .reorderImages(
        this.vehiclePublicId,
        imgs.map((i) => i.publicId)
      )
      .subscribe({
        next: () => this.loadVehicle(),
        error: (err) => this.error.set(err?.error?.detail || 'Could not reorder images.'),
      });
  }

  deleteImage(imagePublicId: string): void {
    this.partnerService.deleteImage(this.vehiclePublicId, imagePublicId).subscribe({
      next: () => {
        this.message.set('Image removed.');
        this.loadVehicle();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Could not delete image.'),
    });
  }

  onDocFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.docFile = input.files?.[0] || null;
  }

  uploadDocument(): void {
    if (!this.docFile) return;
    this.busy.set(true);
    this.error.set(null);
    this.partnerService
      .uploadDocument(this.vehiclePublicId, {
        documentTypeCode: this.docForm.documentTypeCode,
        documentNumber: this.docForm.documentNumber || undefined,
        issueDate: this.docForm.issueDate || undefined,
        expiryDate: this.docForm.expiryDate || undefined,
        file: this.docFile,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.docFile = null;
          this.docForm.documentNumber = '';
          this.message.set('Compliance document uploaded.');
          this.loadVehicle();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(err?.error?.detail || 'Document upload failed.');
        },
      });
  }

  downloadDocument(doc: VehicleDocumentDto): void {
    this.partnerService.downloadPrivateFile(doc.downloadUrl).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.originalFilename || `${doc.documentTypeCode}.bin`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.error.set(err?.error?.detail || 'Could not download document.'),
    });
  }

  loadPricing(): void {
    this.partnerService.getPricing(this.vehiclePublicId).subscribe({
      next: (ov) => {
        this.pricingOverview.set(ov);
        const p = ov.activePlan;
        this.pricingForm = {
          currency: p.currency,
          hourlyRate: p.hourlyRate,
          dailyRate: p.dailyRate,
          weeklyRate: p.weeklyRate,
          securityDeposit: p.securityDeposit,
          minRentalHours: p.minRentalHours,
          maxRentalDays: p.maxRentalDays,
          includedKmPerDay: p.includedKmPerDay,
          extraKmCharge: p.extraKmCharge,
          extraHourCharge: p.extraHourCharge,
          cleaningFee: p.cleaningFee,
          deliveryFee: p.deliveryFee,
          additionalChargesDescription: p.additionalChargesDescription || '',
          lateReturnFeePerHour: p.lateReturnFeePerHour,
          lateReturnFlatPenalty: p.lateReturnFlatPenalty,
          lateReturnGraceMinutes: p.lateReturnGraceMinutes,
          active: true,
        };
        this.pricingValidFromLocal = p.validFrom ? this.toLocalInput(new Date(p.validFrom)) : '';
        this.pricingValidToLocal = p.validTo ? this.toLocalInput(new Date(p.validTo)) : '';
      },
    });
  }

  savePricing(): void {
    this.busy.set(true);
    this.error.set(null);
    const req: SaveVehiclePricingRequest = {
      ...this.pricingForm,
      validFrom: this.pricingValidFromLocal ? new Date(this.pricingValidFromLocal).toISOString() : null,
      validTo: this.pricingValidToLocal ? new Date(this.pricingValidToLocal).toISOString() : null,
    };
    this.partnerService.savePricing(this.vehiclePublicId, req).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set('Pricing plan saved.');
        this.loadPricing();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Failed to save pricing plan.');
      },
    });
  }

  addRule(): void {
    this.busy.set(true);
    this.error.set(null);
    this.partnerService.addPricingRule(this.vehiclePublicId, this.ruleForm).subscribe({
      next: () => {
        this.busy.set(false);
        this.ruleForm.ruleName = '';
        this.message.set('Pricing rule added.');
        this.loadPricing();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Could not add pricing rule.');
      },
    });
  }

  deactivateRule(ruleId: number): void {
    this.partnerService.deactivatePricingRule(this.vehiclePublicId, ruleId).subscribe({
      next: () => {
        this.message.set('Pricing rule deactivated.');
        this.loadPricing();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Failed to deactivate rule.'),
    });
  }

  loadAvailability(): void {
    const fromIso = this.calFromLocal ? new Date(this.calFromLocal).toISOString() : undefined;
    const toIso = this.calToLocal ? new Date(this.calToLocal).toISOString() : undefined;
    this.partnerService.getAvailability(this.vehiclePublicId, fromIso, toIso).subscribe({
      next: (cal) => this.calendar.set(cal),
      error: (err) => this.error.set(err?.error?.detail || 'Failed to load availability calendar.'),
    });
  }

  createBlock(): void {
    if (!this.blockStartLocal || !this.blockEndLocal) return;
    this.busy.set(true);
    this.error.set(null);
    this.partnerService
      .createBlock(this.vehiclePublicId, {
        startTime: new Date(this.blockStartLocal).toISOString(),
        endTime: new Date(this.blockEndLocal).toISOString(),
        blockType: this.blockForm.blockType,
        reason: this.blockForm.reason,
        notes: this.blockForm.notes || null,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.blockForm.reason = '';
          this.blockForm.notes = '';
          this.message.set('Availability block created.');
          this.loadAvailability();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(err?.error?.detail || 'Could not create availability block.');
        },
      });
  }

  removeBlock(blockPublicId: string): void {
    this.partnerService.removeBlock(this.vehiclePublicId, blockPublicId).subscribe({
      next: () => {
        this.message.set('Availability block cancelled.');
        this.loadAvailability();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Could not remove block.'),
    });
  }

  private toLocalInput(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}

