import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { PartnerInventoryService } from '../../core/services/partner-inventory.service';
import {
  AvailabilityBlockDto,
  LocationDto,
  PartnerInventorySummaryDto,
} from '../../core/models/vehicle.models';

interface EditableHubRow {
  location: LocationDto;
  enabled: boolean;
  primary: boolean;
  pickupAvailable: boolean;
  returnAvailable: boolean;
}

@Component({
  selector: 'app-partner-calendar-hubs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <div class="page-head">
        <div>
          <h1>Fleet Calendar & Pickup Hubs</h1>
          <p class="muted">View upcoming availability blocks across your vehicles and configure operating island hubs.</p>
        </div>
        <a routerLink="/partner/fleet" class="ir-btn ir-btn-outline">← Fleet Inventory</a>
      </div>

      @if (message()) {
        <div class="ir-alert ir-alert-success">{{ message() }}</div>
      }
      @if (error()) {
        <div class="ir-alert ir-alert-error">{{ error() }}</div>
      }

      <div class="ir-card pad mb-md">
        <h3>Upcoming Availability & Maintenance Blocks</h3>
        @if (blocks().length === 0) {
          <p class="muted small mt-sm">No upcoming blocks across your fleet.</p>
        } @else {
          <div class="ir-table-wrap mt-sm">
            <table class="ir-table">
              <thead>
                <tr><th>Vehicle</th><th>Type</th><th>Start</th><th>End</th><th>Reason</th><th>Actions</th></tr>
              </thead>
              <tbody>
                @for (b of blocks(); track b.publicId) {
                  <tr>
                    <td><a [routerLink]="['/partner/fleet', b.vehiclePublicId]">View Vehicle</a></td>
                    <td>{{ b.blockType }}</td>
                    <td class="small">{{ b.startTime | date:'medium' }}</td>
                    <td class="small">{{ b.endTime | date:'medium' }}</td>
                    <td>{{ b.reason }}</td>
                    <td>
                      @if (canWrite() && b.removable && b.status === 'ACTIVE') {
                        <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" (click)="removeBlock(b)">Unblock</button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <div class="ir-card pad">
        <div class="page-head">
          <div>
            <h3>Partner Operating Hubs (Port Blair, Havelock & Neil)</h3>
            <p class="muted small">Enable hubs where your business offers pickup and return service.</p>
          </div>
          @if (canWrite()) {
            <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" (click)="saveHubs()">Save Hub Configuration</button>
          }
        </div>

        <div class="ir-table-wrap">
          <table class="ir-table">
            <thead>
              <tr><th>Operating</th><th>Hub Name</th><th>Island</th><th>Primary</th><th>Pickup</th><th>Return</th></tr>
            </thead>
            <tbody>
              @for (row of hubRows(); track row.location.publicId) {
                <tr>
                  <td><input type="checkbox" [(ngModel)]="row.enabled" [disabled]="!canWrite()" /></td>
                  <td><strong>{{ row.location.name }}</strong><div class="muted small">{{ row.location.address }}</div></td>
                  <td>{{ row.location.islandName }}</td>
                  <td><input type="radio" name="primaryHub" [checked]="row.primary" [disabled]="!canWrite() || !row.enabled" (change)="setPrimary(row.location.publicId)" /></td>
                  <td><input type="checkbox" [(ngModel)]="row.pickupAvailable" [disabled]="!canWrite() || !row.enabled" /></td>
                  <td><input type="checkbox" [(ngModel)]="row.returnAvailable" [disabled]="!canWrite() || !row.enabled" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`h3 { font-size: 1.05rem; margin-bottom: 0.25rem; }`],
})
export class PartnerCalendarHubsComponent implements OnInit {
  private readonly partnerService = inject(PartnerInventoryService);
  private readonly catalogService = inject(CatalogService);
  private readonly authService = inject(AuthService);

  blocks = signal<AvailabilityBlockDto[]>([]);
  hubRows = signal<EditableHubRow[]>([]);
  summary = signal<PartnerInventorySummaryDto | null>(null);
  message = signal<string | null>(null);
  error = signal<string | null>(null);

  readonly canWrite = computed(() => {
    const roles = this.authService.currentUser()?.roles ?? [];
    const hasRole = roles.includes('ROLE_PARTNER_OWNER') || roles.includes('ROLE_PARTNER_MANAGER');
    return hasRole && this.summary()?.businessVerificationStatus === 'APPROVED';
  });

  ngOnInit(): void {
    this.partnerService.getSummary().subscribe({ next: (s) => this.summary.set(s) });
    this.loadBlocks();
    this.loadHubs();
  }

  loadBlocks(): void {
    this.partnerService.listUpcomingBlocks().subscribe({
      next: (d) => this.blocks.set(d),
      error: (err) => this.error.set(err?.error?.detail || 'Could not load upcoming calendar blocks.'),
    });
  }

  loadHubs(): void {
    this.catalogService.getHubs().subscribe({
      next: (allHubs) => {
        this.partnerService.getBusinessLocations().subscribe({
          next: (configured) => {
            const byPublicId = new Map(configured.map((c) => [c.location.publicId, c]));
            this.hubRows.set(
              allHubs.map((loc) => {
                const existing = byPublicId.get(loc.publicId);
                return {
                  location: loc,
                  enabled: !!existing,
                  primary: existing?.primary ?? false,
                  pickupAvailable: existing ? existing.pickupAvailable : true,
                  returnAvailable: existing ? existing.returnAvailable : true,
                };
              })
            );
          },
        });
      },
    });
  }

  setPrimary(locationPublicId: string): void {
    this.hubRows.update((rows) =>
      rows.map((r) => ({ ...r, primary: r.location.publicId === locationPublicId }))
    );
  }

  saveHubs(): void {
    this.error.set(null);
    this.message.set(null);
    const enabled = this.hubRows().filter((r) => r.enabled);
    this.partnerService
      .updateBusinessLocations({
        locations: enabled.map((r) => ({
          locationPublicId: r.location.publicId,
          primary: r.primary,
          pickupAvailable: r.pickupAvailable,
          returnAvailable: r.returnAvailable,
        })),
      })
      .subscribe({
        next: () => {
          this.message.set('Partner operating hubs updated.');
          this.loadHubs();
        },
        error: (err) => this.error.set(err?.error?.detail || 'Failed to update partner hubs.'),
      });
  }

  removeBlock(b: AvailabilityBlockDto): void {
    this.partnerService.removeBlock(b.vehiclePublicId, b.publicId).subscribe({
      next: () => {
        this.message.set('Block cancelled.');
        this.loadBlocks();
      },
      error: (err) => this.error.set(err?.error?.detail || 'Failed to remove block.'),
    });
  }
}

