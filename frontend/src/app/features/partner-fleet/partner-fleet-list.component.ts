import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { PartnerInventoryService } from '../../core/services/partner-inventory.service';
import {
  IslandDto,
  PartnerInventorySummaryDto,
  PartnerVehicleDto,
  VehicleCategoryDto,
  VehicleDocumentDto,
  VehicleStatus,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-partner-fleet-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Partner Fleet Inventory</h1>
          <p class="muted">Manage Andaman & Nicobar vehicles, media, compliance documents, pricing & availability.</p>
        </div>
        <div class="actions-row">
          @if (canWrite()) {
            <a routerLink="/partner/fleet/new" class="ir-btn ir-btn-primary">+ Add Vehicle</a>
          }
          <a routerLink="/partner/calendar" class="ir-btn ir-btn-outline">Calendar & Hubs</a>
        </div>
      </div>

      @if (!hasWriteRole()) {
        <div class="ir-alert ir-alert-info">
          Signed in as <strong>Partner Staff</strong> (read-only inventory access). Contact a business Owner or Manager to add, edit, or publish vehicles.
        </div>
      } @else if (summary() && summary()!.businessVerificationStatus !== 'APPROVED') {
        <div class="ir-alert ir-alert-warn">
          Business verification status: <strong>{{ summary()!.businessVerificationStatus }}</strong>. Only <strong>APPROVED</strong> rental partners can create, modify, or submit vehicles for listing.
        </div>
      }

      @if (message()) {
        <div class="ir-alert ir-alert-success">{{ message() }}</div>
      }
      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      @if (summary(); as s) {
        <div class="kpi-grid">
          <div class="kpi-card"><div class="kpi-label">Total Fleet</div><div class="kpi-value">{{ s.totalVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Active Listings</div><div class="kpi-value">{{ s.activeVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Pending Review</div><div class="kpi-value">{{ s.pendingReviewVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Drafts</div><div class="kpi-value">{{ s.draftVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Paused / Maint.</div><div class="kpi-value">{{ s.pausedOrMaintenanceVehicles }}</div></div>
          <div class="kpi-card"><div class="kpi-label">Docs Expiring (30d)</div><div class="kpi-value">{{ s.expiringDocumentsCount }}</div></div>
        </div>
      }

      @if (expiringDocs().length > 0) {
        <div class="ir-alert ir-alert-warn">
          <strong>Compliance Alerts ({{ expiringDocs().length }}):</strong>
          <ul class="exp-list">
            @for (doc of expiringDocs().slice(0, 4); track doc.publicId) {
              <li>
                <a [routerLink]="['/partner/fleet', doc.vehiclePublicId]">{{ doc.documentTypeName }}</a>
                — expires {{ doc.expiryDate }} ({{ doc.expired ? 'EXPIRED' : 'Expiring soon' }})
              </li>
            }
          </ul>
        </div>
      }

      <div class="ir-card pad mb-md">
        <div class="filter-toolbar">
          <input type="text" [(ngModel)]="search" placeholder="Search title, make, registration..." (keyup.enter)="loadVehicles(true)" />
          <select [(ngModel)]="status" (change)="loadVehicles(true)">
            <option value="">All Statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="PENDING_REVIEW">Pending Review</option>
            <option value="ACTIVE">Active</option>
            <option value="REJECTED">Rejected</option>
            <option value="PAUSED">Paused</option>
            <option value="MAINTENANCE">Maintenance</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="ARCHIVED">Archived</option>
          </select>
          <select [(ngModel)]="islandId" (change)="loadVehicles(true)">
            <option [ngValue]="null">All Islands</option>
            @for (isl of islands(); track isl.id) {
              <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
            }
          </select>
          <select [(ngModel)]="categoryId" (change)="loadVehicles(true)">
            <option [ngValue]="null">All Categories</option>
            @for (cat of categories(); track cat.id) {
              <option [ngValue]="cat.id">{{ cat.name }}</option>
            }
          </select>
          <button type="button" class="ir-btn ir-btn-outline" (click)="loadVehicles(true)">Filter</button>
        </div>
      </div>

      <div class="ir-card">
        @if (loading()) {
          <div class="pad muted">Loading fleet inventory...</div>
        } @else if (vehicles().length === 0) {
          <div class="pad muted">
            No vehicles match your filter criteria.
            @if (canWrite()) {
              <a routerLink="/partner/fleet/new">Add your first vehicle</a>
            }
          </div>
        } @else {
          <div class="ir-table-wrap">
            <table class="ir-table">
              <thead>
                <tr>
                  <th>Vehicle</th>
                  <th>Reg. No.</th>
                  <th>Island & Hub</th>
                  <th>Daily Rate</th>
                  <th>Status</th>
                  <th>Docs / Media</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (v of vehicles(); track v.publicId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/partner/fleet', v.publicId]" class="veh-link"><strong>{{ v.title }}</strong></a>
                      <div class="muted small">{{ v.category.name }} · {{ v.make }} {{ v.model }} ({{ v.modelYear }})</div>
                      @if (v.rejectionReason) {
                        <div class="small err-text">Rejected: {{ v.rejectionReason }}</div>
                      }
                      @if (v.suspensionReason) {
                        <div class="small err-text">Suspended: {{ v.suspensionReason }}</div>
                      }
                    </td>
                    <td><code>{{ v.registrationNumber }}</code></td>
                    <td>
                      <div>{{ v.island.displayAlias || v.island.name }}</div>
                      <div class="muted small">{{ v.pickupLocation?.name || 'Island pickup' }}</div>
                    </td>
                    <td>
                      <strong>₹{{ v.dailyRate | number:'1.0-0' }}</strong>
                      <div class="muted small">Dep: ₹{{ v.securityDeposit | number:'1.0-0' }}</div>
                    </td>
                    <td><span class="status-pill status-{{ v.status }}">{{ v.status }}</span></td>
                    <td class="small">
                      <div>Photos: {{ v.images.length }}</div>
                      <div>Docs: {{ v.documents.length }}</div>
                    </td>
                    <td>
                      <div class="actions-row">
                        <a [routerLink]="['/partner/fleet', v.publicId]" class="ir-btn ir-btn-outline ir-btn-sm">Manage</a>
                        @if (canWrite() && v.status !== 'ARCHIVED') {
                          <a [routerLink]="['/partner/fleet', v.publicId, 'edit']" class="ir-btn ir-btn-outline ir-btn-sm">Edit</a>
                        }
                        @if (canWrite() && (v.status === 'DRAFT' || v.status === 'REJECTED')) {
                          <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" (click)="runAction(v, 'submit')">Submit</button>
                        }
                        @if (canWrite() && v.status === 'ACTIVE') {
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="runAction(v, 'pause')">Pause</button>
                        }
                        @if (canWrite() && (v.status === 'PAUSED' || v.status === 'MAINTENANCE')) {
                          <button type="button" class="ir-btn ir-btn-secondary ir-btn-sm" (click)="runAction(v, 'resume')">Resume</button>
                        }
                        @if (canWrite() && (v.status === 'ACTIVE' || v.status === 'PAUSED')) {
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="runAction(v, 'maintenance')">Maint.</button>
                        }
                        @if (canWrite() && v.status !== 'ARCHIVED' && v.status !== 'PENDING_REVIEW') {
                          <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="runAction(v, 'archive')">Archive</button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (totalPages() > 1) {
            <div class="pager pad">
              <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" [disabled]="page() === 0" (click)="loadVehicles(false, page() - 1)">Previous</button>
              <span class="muted small">Page {{ page() + 1 }} of {{ totalPages() }}</span>
              <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="loadVehicles(false, page() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .filter-toolbar { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 0.65rem; }
    .filter-toolbar input, .filter-toolbar select { padding: 0.5rem 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.875rem; }
    .exp-list { margin: 0.35rem 0 0 1.25rem; }
    .err-text { color: #b91c1c; margin-top: 0.2rem; }
    .pager { display: flex; justify-content: flex-end; align-items: center; gap: 0.75rem; }
    @media (max-width: 900px) { .filter-toolbar { grid-template-columns: 1fr; } }
  `],
})
export class PartnerFleetListComponent implements OnInit {
  private readonly partnerService = inject(PartnerInventoryService);
  private readonly catalogService = inject(CatalogService);
  private readonly authService = inject(AuthService);

  summary = signal<PartnerInventorySummaryDto | null>(null);
  vehicles = signal<PartnerVehicleDto[]>([]);
  expiringDocs = signal<VehicleDocumentDto[]>([]);
  islands = signal<IslandDto[]>([]);
  categories = signal<VehicleCategoryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  message = signal<string | null>(null);
  page = signal<number>(0);
  totalPages = signal<number>(0);

  search = '';
  status: VehicleStatus | '' = '';
  islandId: number | null = null;
  categoryId: number | null = null;

  readonly hasWriteRole = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    return roles.includes('ROLE_PARTNER_OWNER') || roles.includes('ROLE_PARTNER_MANAGER');
  });

  readonly canWrite = computed(
    () => this.hasWriteRole() && this.summary()?.businessVerificationStatus === 'APPROVED'
  );

  ngOnInit(): void {
    this.catalogService.getIslands().subscribe({ next: (d) => this.islands.set(d) });
    this.catalogService.getCategories().subscribe({ next: (d) => this.categories.set(d) });
    this.loadSummary();
    this.loadExpiringDocs();
    this.loadVehicles(true);
  }

  loadSummary(): void {
    this.partnerService.getSummary().subscribe({
      next: (s) => this.summary.set(s),
      error: (err) => this.error.set(err?.error?.detail || 'Failed to load partner summary.'),
    });
  }

  loadExpiringDocs(): void {
    this.partnerService.listExpiringDocuments(30).subscribe({
      next: (d) => this.expiringDocs.set(d),
    });
  }

  loadVehicles(resetPage: boolean, targetPage?: number): void {
    if (resetPage) this.page.set(0);
    else if (targetPage !== undefined) this.page.set(targetPage);

    this.loading.set(true);
    this.partnerService
      .listVehicles({
        status: this.status,
        islandId: this.islandId,
        categoryId: this.categoryId,
        search: this.search || undefined,
        page: this.page(),
        size: 15,
      })
      .subscribe({
        next: (res) => {
          this.vehicles.set(res.content);
          this.totalPages.set(res.totalPages);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.detail || 'Failed to load fleet list.');
          this.loading.set(false);
        },
      });
  }

  runAction(
    v: PartnerVehicleDto,
    action: 'submit' | 'pause' | 'resume' | 'maintenance' | 'archive'
  ): void {
    this.error.set(null);
    this.message.set(null);

    const obs =
      action === 'submit'
        ? this.partnerService.submitForReview(v.publicId)
        : action === 'pause'
          ? this.partnerService.pauseVehicle(v.publicId)
          : action === 'resume'
            ? this.partnerService.resumeVehicle(v.publicId)
            : action === 'maintenance'
              ? this.partnerService.markMaintenance(v.publicId)
              : this.partnerService.archiveVehicle(v.publicId);

    obs.subscribe({
      next: (updated) => {
        this.message.set(`${updated.title} updated to status ${updated.status}.`);
        this.loadSummary();
        this.loadVehicles(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || `Could not perform ${action} action.`);
      },
    });
  }
}

