import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminFleetService } from '../../core/services/admin-fleet.service';
import { AdminVehicleDto, VehicleDocumentDto } from '../../core/models/vehicle.models';

@Component({
  selector: 'app-admin-vehicle-review',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ir-page">
      <a routerLink="/admin/fleet" class="link-btn">← Back to Moderation Queue</a>

      @if (message()) {
        <div class="ir-alert ir-alert-success mt-sm">{{ message() }}</div>
      }
      @if (error()) {
        <div class="ir-alert ir-alert-error mt-sm">{{ error() }}</div>
      }

      @if (loading()) {
        <div class="ir-card pad mt-sm muted">Loading vehicle inspection dossier...</div>
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
              <p class="muted">Partner: <strong>{{ v.businessTradeName }}</strong> ({{ v.businessLegalName }}) · KYC: <span class="status-pill status-{{ v.businessVerificationStatus }}">{{ v.businessVerificationStatus }}</span></p>
            </div>

            <div class="actions-row">
              @if (v.status === 'PENDING_REVIEW') {
                <button type="button" class="ir-btn ir-btn-secondary ir-btn-sm" [disabled]="busy()" (click)="approve()">Approve Listing</button>
                <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" [disabled]="busy()" (click)="openModal('reject')">Reject Listing</button>
              }
              @if (v.status === 'ACTIVE' || v.status === 'PAUSED' || v.status === 'MAINTENANCE') {
                <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" [disabled]="busy()" (click)="openModal('suspend')">Suspend Listing</button>
              }
              @if (v.status === 'SUSPENDED') {
                <button type="button" class="ir-btn ir-btn-primary ir-btn-sm" [disabled]="busy()" (click)="unsuspend()">Unsuspend</button>
              }
            </div>
          </div>

          @if (v.rejectionReason) {
            <div class="ir-alert ir-alert-error"><strong>Rejection Reason:</strong> {{ v.rejectionReason }}</div>
          }
          @if (v.suspensionReason) {
            <div class="ir-alert ir-alert-error"><strong>Suspension Reason:</strong> {{ v.suspensionReason }}</div>
          }

          <div class="spec-grid mt-sm">
            <div><span class="muted small">Make / Model</span><div><strong>{{ v.make }} {{ v.model }} ({{ v.modelYear }})</strong></div></div>
            <div><span class="muted small">Transmission / Fuel</span><div><strong>{{ v.transmissionType }} · {{ v.fuelType }}</strong></div></div>
            <div><span class="muted small">Seats / Buffer</span><div><strong>{{ v.seatingCapacity }} seats · {{ v.turnaroundBufferMinutes }} min</strong></div></div>
            <div><span class="muted small">Daily Rate / Deposit</span><div><strong>₹{{ v.dailyRate }} / ₹{{ v.securityDeposit }}</strong></div></div>
            <div><span class="muted small">Pickup Hub</span><div><strong>{{ v.pickupLocation?.name || 'Island-wide' }}</strong></div></div>
            <div><span class="muted small">Return Policy</span><div><strong>{{ v.allowDifferentReturnSameIsland ? (v.returnLocation?.name || 'Same island flexible') : 'Same hub only' }}</strong></div></div>
          </div>
        </div>

        <div class="ir-card pad mb-md">
          <h3>Submitted Photos ({{ v.images.length }})</h3>
          @if (v.images.length === 0) {
            <p class="muted small mt-sm">No photos uploaded.</p>
          } @else {
            <div class="img-grid mt-sm">
              @for (img of v.images; track img.publicId) {
                <div class="img-tile">
                  @if (imagePreviews()[img.publicId]) {
                    <img [src]="imagePreviews()[img.publicId]" [alt]="img.originalFilename || v.title" />
                  } @else {
                    <span class="muted small">Loading photo...</span>
                  }
                </div>
              }
            </div>
          }
        </div>

        <div class="ir-card pad mb-md">
          <h3>Compliance Document Verification ({{ v.documents.length }})</h3>
          @if (v.documents.length === 0) {
            <p class="muted small mt-sm">No compliance documents uploaded.</p>
          } @else {
            <div class="ir-table-wrap mt-sm">
              <table class="ir-table">
                <thead><tr><th>Document Type</th><th>Number</th><th>Dates</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>
                  @for (doc of v.documents; track doc.publicId) {
                    <tr>
                      <td><strong>{{ doc.documentTypeName }}</strong><div class="muted small">{{ doc.documentTypeCode }}</div></td>
                      <td>{{ doc.documentNumber || '—' }}</td>
                      <td class="small">
                        <div>Issued: {{ doc.issueDate || '—' }}</div>
                        <div>Expiry: {{ doc.expiryDate || 'N/A' }}
                          @if (doc.expired) { <span class="badge status-EXPIRED">EXPIRED</span> }
                          @else if (doc.expiringSoon) { <span class="badge status-PENDING_REVIEW">Expiring soon</span> }
                        </div>
                      </td>
                      <td>
                        <span class="status-pill status-{{ doc.verificationStatus }}">{{ doc.verificationStatus }}</span>
                        @if (doc.rejectionReason) { <div class="muted small">{{ doc.rejectionReason }}</div> }
                      </td>
                      <td>
                        <div class="actions-row">
                          <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="downloadDoc(doc)">Download</button>
                          <button type="button" class="ir-btn ir-btn-secondary ir-btn-sm" [disabled]="busy()" (click)="verifyDoc(doc.publicId, 'VERIFIED')">Verify</button>
                          <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" [disabled]="busy()" (click)="openDocRejectModal(doc.publicId)">Reject</button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <div class="two-col">
          <div class="ir-card pad">
            <h3>Status History</h3>
            <table class="ir-table mt-sm">
              <thead><tr><th>Time</th><th>Transition</th><th>Reason</th></tr></thead>
              <tbody>
                @for (h of v.statusHistory; track h.id) {
                  <tr>
                    <td class="small">{{ h.createdAt | date:'short' }}</td>
                    <td class="small">{{ h.fromStatus || 'NEW' }} → <strong>{{ h.toStatus }}</strong></td>
                    <td class="small">{{ h.reason || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="ir-card pad">
            <h3>Audit Log Trail</h3>
            <table class="ir-table mt-sm">
              <thead><tr><th>Time</th><th>Action</th><th>Actor</th></tr></thead>
              <tbody>
                @for (a of v.auditLogs; track a.id) {
                  <tr>
                    <td class="small">{{ a.createdAt | date:'short' }}</td>
                    <td class="small"><strong>{{ a.action }}</strong></td>
                    <td class="small">{{ a.actorUserId ?? 'system' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

      @if (modalMode()) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>{{ modalTitle() }}</h3>
            <div class="field mt-sm">
              <label for="modReason">Reason (required) *</label>
              <textarea id="modReason" rows="3" [(ngModel)]="modalReason" placeholder="Provide clear moderation or document rejection notes..."></textarea>
            </div>
            <div class="actions-row">
              <button type="button" class="ir-btn ir-btn-danger ir-btn-sm" [disabled]="!modalReason.trim() || busy()" (click)="confirmModal()">Confirm</button>
              <button type="button" class="ir-btn ir-btn-outline ir-btn-sm" (click)="closeModal()">Cancel</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .spec-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 0.75rem; background: #f8fafc; padding: 0.85rem; border-radius: 8px; }
    .img-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 0.75rem; }
    .img-tile { height: 130px; border: 1px solid var(--color-border); border-radius: 8px; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #f1f5f9; }
    .img-tile img { width: 100%; height: 100%; object-fit: cover; }
    .two-col { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
    @media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }
  `],
})
export class AdminVehicleReviewComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly adminService = inject(AdminFleetService);

  vehicle = signal<AdminVehicleDto | null>(null);
  imagePreviews = signal<Record<string, string>>({});
  loading = signal<boolean>(true);
  busy = signal<boolean>(false);
  message = signal<string | null>(null);
  error = signal<string | null>(null);

  modalMode = signal<'reject' | 'suspend' | 'docReject' | null>(null);
  modalReason = '';
  targetDocPublicId = '';

  private vehiclePublicId = '';

  ngOnInit(): void {
    this.vehiclePublicId = this.route.snapshot.paramMap.get('publicId') || '';
    this.loadDetail();
  }

  ngOnDestroy(): void {
    this.revokePreviews();
  }

  modalTitle(): string {
    if (this.modalMode() === 'reject') return 'Reject Vehicle Listing';
    if (this.modalMode() === 'suspend') return 'Suspend Vehicle Listing';
    return 'Reject Compliance Document';
  }

  loadDetail(): void {
    this.loading.set(true);
    this.adminService.getVehicleDetail(this.vehiclePublicId).subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.loading.set(false);
        this.loadImages(v);
      },
      error: (err) => {
        this.error.set(err?.error?.detail || 'Failed to load vehicle inspection details.');
        this.loading.set(false);
      },
    });
  }

  private loadImages(v: AdminVehicleDto): void {
    this.revokePreviews();
    for (const img of v.images) {
      this.adminService.downloadImagePreview(v.publicId, img.publicId).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          this.imagePreviews.update((m) => ({ ...m, [img.publicId]: url }));
        },
      });
    }
  }

  private revokePreviews(): void {
    Object.values(this.imagePreviews()).forEach((u) => URL.revokeObjectURL(u));
    this.imagePreviews.set({});
  }

  approve(): void {
    this.busy.set(true);
    this.error.set(null);
    this.adminService.approveVehicle(this.vehiclePublicId, 'Approved after compliance verification').subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.busy.set(false);
        this.message.set('Vehicle approved and published to public catalog.');
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Could not approve vehicle.');
      },
    });
  }

  unsuspend(): void {
    this.busy.set(true);
    this.error.set(null);
    this.adminService.unsuspendVehicle(this.vehiclePublicId, 'Suspension lifted after review').subscribe({
      next: (v) => {
        this.vehicle.set(v);
        this.busy.set(false);
        this.message.set(`Vehicle unsuspended (status: ${v.status}).`);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Could not unsuspend vehicle.');
      },
    });
  }

  verifyDoc(docPublicId: string, status: 'VERIFIED' | 'REJECTED', reason?: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.adminService.verifyDocument(this.vehiclePublicId, docPublicId, status, reason).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set(`Document marked ${status}.`);
        this.loadDetail();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail || 'Document verification update failed.');
      },
    });
  }

  downloadDoc(doc: VehicleDocumentDto): void {
    this.adminService.downloadDocument(this.vehiclePublicId, doc.publicId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.originalFilename || `${doc.documentTypeCode}.bin`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.error.set(err?.error?.detail || 'Failed to download private document.'),
    });
  }

  openModal(mode: 'reject' | 'suspend'): void {
    this.modalReason = '';
    this.modalMode.set(mode);
  }

  openDocRejectModal(docPublicId: string): void {
    this.modalReason = '';
    this.targetDocPublicId = docPublicId;
    this.modalMode.set('docReject');
  }

  closeModal(): void {
    this.modalMode.set(null);
    this.modalReason = '';
    this.targetDocPublicId = '';
  }

  confirmModal(): void {
    const mode = this.modalMode();
    const reason = this.modalReason.trim();
    if (!mode || !reason) return;

    this.busy.set(true);
    this.error.set(null);
    if (mode === 'reject') {
      this.adminService.rejectVehicle(this.vehiclePublicId, reason).subscribe({
        next: (v) => {
          this.vehicle.set(v);
          this.busy.set(false);
          this.message.set('Vehicle rejected.');
          this.closeModal();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(err?.error?.detail || 'Rejection failed.');
        },
      });
    } else if (mode === 'suspend') {
      this.adminService.suspendVehicle(this.vehiclePublicId, reason).subscribe({
        next: (v) => {
          this.vehicle.set(v);
          this.busy.set(false);
          this.message.set('Vehicle suspended.');
          this.closeModal();
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(err?.error?.detail || 'Suspension failed.');
        },
      });
    } else {
      const docId = this.targetDocPublicId;
      this.closeModal();
      this.verifyDoc(docId, 'REJECTED', reason);
    }
  }
}

