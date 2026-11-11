import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminFleetService } from '../../core/services/admin-fleet.service';
import { CatalogService } from '../../core/services/catalog.service';
import {
  AdminVehicleDto,
  IslandDto,
  VehicleCategoryDto,
  VehicleDocumentDto,
  VehicleStatus,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-admin-moderation-queue',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Admin Fleet Moderation Queue</h1>
          <p class="muted">Inspect partner vehicle submissions, verify compliance documents, and govern fleet status.</p>
        </div>
        <a routerLink="/admin/catalog-config" class="ir-btn ir-btn-outline">Catalog & Island Config</a>
      </div>

      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      @if (expiringDocs().length > 0) {
        <div class="ir-alert ir-alert-warn">
          <strong>Expiring / Expired Compliance Documents (30 days): {{ expiringDocs().length }}</strong>
          <ul class="exp-list">
            @for (doc of expiringDocs().slice(0, 5); track doc.publicId) {
              <li>
                <a [routerLink]="['/admin/fleet', doc.vehiclePublicId]">{{ doc.documentTypeName }}</a>
                — expires {{ doc.expiryDate }} ({{ doc.expired ? 'EXPIRED' : 'Expiring soon' }})
              </li>
            }
          </ul>
        </div>
      }

      <div class="ir-card pad mb-md">
        <div class="filter-bar">
          <input type="text" [(ngModel)]="search" placeholder="Search registration, make, model, title..." (keyup.enter)="loadQueue(true)" />
          <select [(ngModel)]="status" (change)="loadQueue(true)">
            <option value="">All Statuses</option>
            <option value="PENDING_REVIEW">Pending Review</option>
            <option value="ACTIVE">Active</option>
            <option value="REJECTED">Rejected</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="DRAFT">Draft</option>
            <option value="PAUSED">Paused</option>
            <option value="MAINTENANCE">Maintenance</option>
            <option value="ARCHIVED">Archived</option>
          </select>
          <select [(ngModel)]="islandId" (change)="loadQueue(true)">
            <option [ngValue]="null">All Islands</option>
            @for (isl of islands(); track isl.id) {
              <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
            }
          </select>
          <select [(ngModel)]="categoryId" (change)="loadQueue(true)">
            <option [ngValue]="null">All Categories</option>
            @for (cat of categories(); track cat.id) {
              <option [ngValue]="cat.id">{{ cat.name }}</option>
            }
          </select>
          <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" (click)="loadQueue(true)">Search</button>
        </div>
      </div>

      <div class="ir-card">
        @if (loading()) {
          <div class="pad muted">Loading fleet moderation queue...</div>
        } @else if (vehicles().length === 0) {
          <div class="pad muted">No vehicles match the selected moderation filters.</div>
        } @else {
          <div class="ir-table-wrap">
            <table class="ir-table">
              <thead>
                <tr>
                  <th>Vehicle</th>
                  <th>Partner Business</th>
                  <th>Island / Hub</th>
                  <th>Reg. No.</th>
                  <th>Status</th>
                  <th>Compliance</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                @for (v of vehicles(); track v.publicId) {
                  <tr>
                    <td>
                      <strong>{{ v.title }}</strong>
                      <div class="muted small">{{ v.category.name }} · {{ v.make }} {{ v.model }} ({{ v.modelYear }})</div>
                    </td>
                    <td>
                      <div>{{ v.businessTradeName }}</div>
                      <div class="muted small">{{ v.businessLegalName }}</div>
                      <span class="status-pill status-{{ v.businessVerificationStatus }}">{{ v.businessVerificationStatus }}</span>
                    </td>
                    <td>
                      <div>{{ v.island.displayAlias || v.island.name }}</div>
                      <div class="muted small">{{ v.pickupLocation?.name || 'Island pickup' }}</div>
                    </td>
                    <td><code>{{ v.registrationNumber }}</code></td>
                    <td><span class="status-pill status-{{ v.status }}">{{ v.status }}</span></td>
                    <td class="small">
                      <div>Docs: {{ v.documents.length }}</div>
                      <div>Photos: {{ v.images.length }}</div>
                    </td>
                    <td>
                      <a [routerLink]="['/admin/fleet', v.publicId]" class="ir-btn ir-btn-outline ir-btn-sm">Inspect & Moderate</a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          @if (totalPages() > 1) {
            <div class="pager pad">
              <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" [disabled]="page() === 0" (click)="loadQueue(false, page() - 1)">Previous</button>
              <span class="muted small">Page {{ page() + 1 }} of {{ totalPages() }}</span>
              <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="loadQueue(false, page() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .filter-bar { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 0.65rem; }
    .filter-bar input, .filter-bar select { padding: 0.5rem 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.875rem; }
    .exp-list { margin: 0.35rem 0 0 1.25rem; }
    .pager { display: flex; justify-content: flex-end; align-items: center; gap: 0.75rem; }
    @media (max-width: 900px) { .filter-bar { grid-template-columns: 1fr; } }
  `],
})
export class AdminModerationQueueComponent implements OnInit {
  private readonly adminService = inject(AdminFleetService);
  private readonly catalogService = inject(CatalogService);

  vehicles = signal<AdminVehicleDto[]>([]);
  expiringDocs = signal<VehicleDocumentDto[]>([]);
  islands = signal<IslandDto[]>([]);
  categories = signal<VehicleCategoryDto[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  page = signal<number>(0);
  totalPages = signal<number>(0);

  search = '';
  status: VehicleStatus | '' = 'PENDING_REVIEW';
  islandId: number | null = null;
  categoryId: number | null = null;

  ngOnInit(): void {
    this.catalogService.getIslands().subscribe({ next: (d) => this.islands.set(d) });
    this.catalogService.getCategories().subscribe({ next: (d) => this.categories.set(d) });
    this.adminService.listExpiringDocuments(30).subscribe({ next: (d) => this.expiringDocs.set(d) });
    this.loadQueue(true);
  }

  loadQueue(resetPage: boolean, targetPage?: number): void {
    if (resetPage) this.page.set(0);
    else if (targetPage !== undefined) this.page.set(targetPage);

    this.loading.set(true);
    this.error.set(null);
    this.adminService
      .listModerationQueue({
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
          this.error.set(err?.error?.detail || 'Failed to load admin moderation queue.');
          this.loading.set(false);
        },
      });
  }
}

