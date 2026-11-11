import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminFleetService } from '../../core/services/admin-fleet.service';
import {
  IslandDto,
  LocationDto,
  VehicleCategoryDto,
  VehicleDocumentTypeDto,
  VehicleType,
} from '../../core/models/vehicle.models';

@Component({
  selector: 'app-admin-catalog-config',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Platform Catalog, Document Types & Island Geography</h1>
          <p class="muted">Configure Andaman & Nicobar islands, pickup hubs, dynamic vehicle categories & required compliance documents.</p>
        </div>
        <a routerLink="/admin/fleet" class="ir-btn ir-btn-outline">← Fleet Moderation</a>
      </div>

      @if (message()) {
        <div class="ir-alert ir-alert-success">{{ message() }}</div>
      }
      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      <div class="tabs">
        <button type="button" class="tab-btn" [class.active]="tab() === 'categories'" (click)="tab.set('categories')">Vehicle Categories ({{ categories().length }})</button>
        <button type="button" class="tab-btn" [class.active]="tab() === 'docTypes'" (click)="tab.set('docTypes')">Document Types ({{ docTypes().length }})</button>
        <button type="button" class="tab-btn" [class.active]="tab() === 'islands'" (click)="tab.set('islands')">Islands ({{ islands().length }})</button>
        <button type="button" class="tab-btn" [class.active]="tab() === 'hubs'" (click)="tab.set('hubs')">Pickup & Return Hubs ({{ locations().length }})</button>
      </div>

      <!-- CATEGORIES -->
      @if (tab() === 'categories') {
        <div class="ir-card pad mb-md">
          <h3>Create Vehicle Category</h3>
          <form class="field-row-3 mt-sm" (ngSubmit)="createCategory()">
            <div class="field"><label>Name *</label><input type="text" [(ngModel)]="catForm.name" name="cName" placeholder="Touring Bike" required /></div>
            <div class="field"><label>Code *</label><input type="text" [(ngModel)]="catForm.code" name="cCode" placeholder="TOURING_BIKE" required /></div>
            <div class="field"><label>Slug *</label><input type="text" [(ngModel)]="catForm.slug" name="cSlug" placeholder="touring-bikes" required /></div>
            <div class="field">
              <label>Wheel Type</label>
              <select [(ngModel)]="catForm.vehicleType" name="cType">
                <option value="TWO_WHEELER">TWO_WHEELER</option>
                <option value="FOUR_WHEELER">FOUR_WHEELER</option>
                <option value="OTHER">OTHER</option>
              </select>
            </div>
            <div class="field"><label>Attributes (CSV)</label><input type="text" [(ngModel)]="catForm.attrsCsv" name="cAttrs" placeholder="engineCapacityCc,mileageKmpl" /></div>
            <div class="field" style="justify-content: flex-end;"><button type="submit" class="ir-btn ir-btn-primary ir-btn-sm">Add Category</button></div>
          </form>
        </div>

        <div class="ir-card">
          <table class="ir-table">
            <thead><tr><th>Name</th><th>Code / Slug</th><th>Wheel Type</th><th>Attributes</th><th>Status</th><th></th></tr></thead>
            <tbody>
              @for (c of categories(); track c.id) {
                <tr>
                  <td><strong>{{ c.name }}</strong></td>
                  <td class="small"><code>{{ c.code }}</code> / {{ c.slug }}</td>
                  <td>{{ c.vehicleType }}</td>
                  <td class="small">{{ c.applicableAttributes.join(', ') }}</td>
                  <td><span class="status-pill" [class.status-ACTIVE]="c.active" [class.status-ARCHIVED]="!c.active">{{ c.active ? 'ACTIVE' : 'DISABLED' }}</span></td>
                  <td><button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="toggleCategory(c)">{{ c.active ? 'Disable' : 'Enable' }}</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- DOCUMENT TYPES -->
      @if (tab() === 'docTypes') {
        <div class="ir-card pad mb-md">
          <h3>Create Compliance Document Type</h3>
          <form class="field-row-3 mt-sm" (ngSubmit)="createDocType()">
            <div class="field"><label>Code *</label><input type="text" [(ngModel)]="docForm.code" name="dtCode" placeholder="COMMERCIAL_PASS" required /></div>
            <div class="field"><label>Name *</label><input type="text" [(ngModel)]="docForm.name" name="dtName" placeholder="Commercial Island Pass" required /></div>
            <div class="field"><label>Applicable Vehicle Types</label><input type="text" [(ngModel)]="docForm.applicableVehicleTypes" name="dtVeh" placeholder="ALL or FOUR_WHEELER" /></div>
            <label class="small"><input type="checkbox" [(ngModel)]="docForm.mandatory" name="dtMand" /> Mandatory for listing</label>
            <label class="small"><input type="checkbox" [(ngModel)]="docForm.requiresExpiryDate" name="dtExp" /> Requires Expiry Date</label>
            <div><button type="submit" class="ir-btn ir-btn-primary ir-btn-sm">Add Document Type</button></div>
          </form>
        </div>

        <div class="ir-card">
          <table class="ir-table">
            <thead><tr><th>Code</th><th>Name</th><th>Mandatory</th><th>Expiry Required</th><th>Applies To</th><th>Active</th></tr></thead>
            <tbody>
              @for (dt of docTypes(); track dt.id) {
                <tr>
                  <td><code>{{ dt.code }}</code></td>
                  <td><strong>{{ dt.name }}</strong></td>
                  <td>{{ dt.mandatory ? 'Yes' : 'Optional' }}</td>
                  <td>{{ dt.requiresExpiryDate ? 'Yes' : 'No' }}</td>
                  <td>{{ dt.applicableVehicleTypes }}</td>
                  <td><span class="status-pill" [class.status-ACTIVE]="dt.active" [class.status-ARCHIVED]="!dt.active">{{ dt.active ? 'ACTIVE' : 'DISABLED' }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- ISLANDS -->
      @if (tab() === 'islands') {
        <div class="ir-card pad mb-md">
          <h3>Add Island Destination</h3>
          <form class="field-row mt-sm" (ngSubmit)="createIsland()">
            <div class="field"><label>Canonical Name *</label><input type="text" [(ngModel)]="islForm.name" name="iName" placeholder="Diglipur (North Andaman)" required /></div>
            <div class="field"><label>Display Alias</label><input type="text" [(ngModel)]="islForm.displayAlias" name="iAlias" placeholder="Optional dual-naming alias" /></div>
            <div class="field"><label>Code *</label><input type="text" [(ngModel)]="islForm.code" name="iCode" placeholder="DIGLIPUR" required /></div>
            <div class="field"><label>Description</label><input type="text" [(ngModel)]="islForm.description" name="iDesc" /></div>
            <div><button type="submit" class="ir-btn ir-btn-primary ir-btn-sm">Add Island</button></div>
          </form>
        </div>

        <div class="ir-card">
          <table class="ir-table">
            <thead><tr><th>Code</th><th>Name</th><th>Display Alias</th><th>Status</th></tr></thead>
            <tbody>
              @for (isl of islands(); track isl.id) {
                <tr>
                  <td><code>{{ isl.code }}</code></td>
                  <td><strong>{{ isl.name }}</strong><div class="muted small">{{ isl.description }}</div></td>
                  <td>{{ isl.displayAlias || '—' }}</td>
                  <td><span class="status-pill" [class.status-ACTIVE]="isl.active" [class.status-ARCHIVED]="!isl.active">{{ isl.active ? 'ACTIVE' : 'DISABLED' }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- HUB LOCATIONS -->
      @if (tab() === 'hubs') {
        <div class="ir-card pad mb-md">
          <h3>Create Pickup & Return Hub</h3>
          <form class="field-row-3 mt-sm" (ngSubmit)="createLocation()">
            <div class="field">
              <label>Island *</label>
              <select [(ngModel)]="hubForm.islandId" name="hIsl" required>
                @for (isl of islands(); track isl.id) {
                  <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
                }
              </select>
            </div>
            <div class="field"><label>Hub Name *</label><input type="text" [(ngModel)]="hubForm.name" name="hName" placeholder="Haddo Jetty Hub" required /></div>
            <div class="field"><label>Code *</label><input type="text" [(ngModel)]="hubForm.code" name="hCode" placeholder="PB_HADDO" required /></div>
            <div class="field"><label>Address *</label><input type="text" [(ngModel)]="hubForm.address" name="hAddr" required /></div>
            <div class="field"><label>Contact Phone</label><input type="text" [(ngModel)]="hubForm.contactPhone" name="hPhone" /></div>
            <div class="field" style="justify-content: flex-end;"><button type="submit" class="ir-btn ir-btn-primary ir-btn-sm">Add Hub</button></div>
          </form>
        </div>

        <div class="ir-card">
          <table class="ir-table">
            <thead><tr><th>Code</th><th>Hub Name</th><th>Island</th><th>Address</th><th>Status</th></tr></thead>
            <tbody>
              @for (loc of locations(); track loc.id) {
                <tr>
                  <td><code>{{ loc.code }}</code></td>
                  <td><strong>{{ loc.name }}</strong></td>
                  <td>{{ loc.islandName }}</td>
                  <td class="small">{{ loc.address }}</td>
                  <td><span class="status-pill" [class.status-ACTIVE]="loc.active" [class.status-ARCHIVED]="!loc.active">{{ loc.active ? 'ACTIVE' : 'DISABLED' }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [``],
})
export class AdminCatalogConfigComponent implements OnInit {
  private readonly adminService = inject(AdminFleetService);

  tab = signal<'categories' | 'docTypes' | 'islands' | 'hubs'>('categories');
  categories = signal<VehicleCategoryDto[]>([]);
  docTypes = signal<VehicleDocumentTypeDto[]>([]);
  islands = signal<IslandDto[]>([]);
  locations = signal<LocationDto[]>([]);
  message = signal<string | null>(null);
  error = signal<string | null>(null);

  catForm = { name: '', code: '', slug: '', vehicleType: 'TWO_WHEELER' as VehicleType, attrsCsv: 'transmissionType,fuelType,seatingCapacity' };
  docForm = { code: '', name: '', applicableVehicleTypes: 'ALL', mandatory: false, requiresExpiryDate: true };
  islForm = { name: '', displayAlias: '', code: '', description: '' };
  hubForm = { islandId: 0, name: '', code: '', address: '', contactPhone: '' };

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.adminService.listAllCategories().subscribe({ next: (d) => this.categories.set(d) });
    this.adminService.listAllDocumentTypes().subscribe({ next: (d) => this.docTypes.set(d) });
    this.adminService.listAllIslands().subscribe({
      next: (d) => {
        this.islands.set(d);
        if (!this.hubForm.islandId && d.length > 0) this.hubForm.islandId = d[0].id;
      },
    });
    this.adminService.listAllLocations().subscribe({ next: (d) => this.locations.set(d) });
  }

  createCategory(): void {
    this.error.set(null);
    const attrs = this.catForm.attrsCsv.split(',').map((s) => s.trim()).filter(Boolean);
    this.adminService
      .createCategory({
        name: this.catForm.name,
        code: this.catForm.code.toUpperCase(),
        slug: this.catForm.slug.toLowerCase(),
        vehicleType: this.catForm.vehicleType,
        applicableAttributes: attrs,
      })
      .subscribe({
        next: () => {
          this.catForm.name = '';
          this.catForm.code = '';
          this.catForm.slug = '';
          this.message.set('Category created.');
          this.loadAll();
        },
        error: (err) => this.error.set(err?.error?.detail || 'Failed to create category.'),
      });
  }

  toggleCategory(c: VehicleCategoryDto): void {
    this.adminService.updateCategoryStatus(c.id, !c.active).subscribe({
      next: () => {
        this.message.set(`Category ${c.name} updated.`);
        this.loadAll();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Could not update category status.'),
    });
  }

  createDocType(): void {
    this.error.set(null);
    this.adminService
      .createDocumentType({
        code: this.docForm.code.toUpperCase(),
        name: this.docForm.name,
        applicableVehicleTypes: this.docForm.applicableVehicleTypes || 'ALL',
        mandatory: this.docForm.mandatory,
        requiresExpiryDate: this.docForm.requiresExpiryDate,
      })
      .subscribe({
        next: () => {
          this.docForm.code = '';
          this.docForm.name = '';
          this.message.set('Document type created.');
          this.loadAll();
        },
        error: (err) => this.error.set(err?.error?.detail || 'Failed to create document type.'),
      });
  }

  createIsland(): void {
    this.error.set(null);
    this.adminService
      .createIsland({
        name: this.islForm.name,
        displayAlias: this.islForm.displayAlias || null,
        code: this.islForm.code.toUpperCase(),
        description: this.islForm.description || null,
      })
      .subscribe({
        next: () => {
          this.islForm = { name: '', displayAlias: '', code: '', description: '' };
          this.message.set('Island created.');
          this.loadAll();
        },
        error: (err) => this.error.set(err?.error?.detail || 'Failed to create island.'),
      });
  }

  createLocation(): void {
    this.error.set(null);
    this.adminService
      .createLocation({
        islandId: this.hubForm.islandId,
        name: this.hubForm.name,
        code: this.hubForm.code.toUpperCase(),
        address: this.hubForm.address,
        contactPhone: this.hubForm.contactPhone || null,
      })
      .subscribe({
        next: () => {
          this.hubForm.name = '';
          this.hubForm.code = '';
          this.hubForm.address = '';
          this.message.set('Hub location created.');
          this.loadAll();
        },
        error: (err) => this.error.set(err?.error?.detail || 'Failed to create hub location.'),
      });
  }
}

