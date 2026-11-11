import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AdminVehicleDto,
  DocumentVerificationStatus,
  IslandDto,
  LocationDto,
  PageResponse,
  VehicleCategoryDto,
  VehicleDocumentDto,
  VehicleDocumentTypeDto,
  VehicleStatus,
  VehicleType,
} from '../models/vehicle.models';

@Injectable({
  providedIn: 'root',
})
export class AdminFleetService {
  private readonly http = inject(HttpClient);

  listModerationQueue(options: {
    status?: VehicleStatus | '';
    partnerBusinessPublicId?: string;
    categoryId?: number | null;
    islandId?: number | null;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<AdminVehicleDto>> {
    let params = new HttpParams();
    Object.entries(options).forEach(([key, val]) => {
      if (val !== undefined && val !== null && val !== '') {
        params = params.set(key, String(val));
      }
    });
    return this.http.get<PageResponse<AdminVehicleDto>>('/api/v1/admin/vehicles', { params });
  }

  getVehicleDetail(publicId: string): Observable<AdminVehicleDto> {
    return this.http.get<AdminVehicleDto>(`/api/v1/admin/vehicles/${publicId}`);
  }

  approveVehicle(publicId: string, reason?: string): Observable<AdminVehicleDto> {
    return this.http.post<AdminVehicleDto>(`/api/v1/admin/vehicles/${publicId}/approve`, {
      reason: reason || null,
    });
  }

  rejectVehicle(publicId: string, reason: string): Observable<AdminVehicleDto> {
    return this.http.post<AdminVehicleDto>(`/api/v1/admin/vehicles/${publicId}/reject`, { reason });
  }

  suspendVehicle(publicId: string, reason: string): Observable<AdminVehicleDto> {
    return this.http.post<AdminVehicleDto>(`/api/v1/admin/vehicles/${publicId}/suspend`, { reason });
  }

  unsuspendVehicle(publicId: string, reason?: string): Observable<AdminVehicleDto> {
    return this.http.post<AdminVehicleDto>(`/api/v1/admin/vehicles/${publicId}/unsuspend`, {
      reason: reason || null,
    });
  }

  verifyDocument(
    vehiclePublicId: string,
    documentPublicId: string,
    status: DocumentVerificationStatus,
    rejectionReason?: string
  ): Observable<VehicleDocumentDto> {
    return this.http.patch<VehicleDocumentDto>(
      `/api/v1/admin/vehicles/${vehiclePublicId}/documents/${documentPublicId}/verify`,
      { status, rejectionReason: rejectionReason || null }
    );
  }

  listExpiringDocuments(withinDays = 30): Observable<VehicleDocumentDto[]> {
    return this.http.get<VehicleDocumentDto[]>('/api/v1/admin/vehicles/documents/expiring', {
      params: new HttpParams().set('withinDays', withinDays.toString()),
    });
  }

  downloadDocument(vehiclePublicId: string, documentPublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/admin/vehicles/${vehiclePublicId}/documents/${documentPublicId}/download`,
      { responseType: 'blob' }
    );
  }

  downloadImagePreview(vehiclePublicId: string, imagePublicId: string): Observable<Blob> {
    return this.http.get(
      `/api/v1/admin/vehicles/${vehiclePublicId}/images/${imagePublicId}/content`,
      { responseType: 'blob' }
    );
  }

  listAllCategories(): Observable<VehicleCategoryDto[]> {
    return this.http.get<VehicleCategoryDto[]>('/api/v1/admin/categories');
  }

  createCategory(req: {
    name: string;
    code: string;
    slug: string;
    vehicleType: VehicleType;
    description?: string | null;
    iconRef?: string | null;
    applicableAttributes?: string[];
    displayOrder?: number;
    active?: boolean;
  }): Observable<VehicleCategoryDto> {
    return this.http.post<VehicleCategoryDto>('/api/v1/admin/categories', req);
  }

  updateCategoryStatus(id: number, active: boolean): Observable<VehicleCategoryDto> {
    return this.http.patch<VehicleCategoryDto>(`/api/v1/admin/categories/${id}/status`, {}, {
      params: new HttpParams().set('active', String(active)),
    });
  }

  listAllDocumentTypes(): Observable<VehicleDocumentTypeDto[]> {
    return this.http.get<VehicleDocumentTypeDto[]>('/api/v1/admin/document-types');
  }

  createDocumentType(req: {
    code: string;
    name: string;
    description?: string | null;
    mandatory?: boolean;
    requiresExpiryDate?: boolean;
    applicableVehicleTypes?: string;
    active?: boolean;
    displayOrder?: number;
  }): Observable<VehicleDocumentTypeDto> {
    return this.http.post<VehicleDocumentTypeDto>('/api/v1/admin/document-types', req);
  }

  listAllIslands(): Observable<IslandDto[]> {
    return this.http.get<IslandDto[]>('/api/v1/admin/geography/islands');
  }

  createIsland(req: {
    name: string;
    displayAlias?: string | null;
    code: string;
    description?: string | null;
    displayOrder?: number;
    active?: boolean;
  }): Observable<IslandDto> {
    return this.http.post<IslandDto>('/api/v1/admin/geography/islands', req);
  }

  listAllLocations(islandId?: number | null): Observable<LocationDto[]> {
    let params = new HttpParams();
    if (islandId != null) params = params.set('islandId', islandId.toString());
    return this.http.get<LocationDto[]>('/api/v1/admin/geography/locations', { params });
  }

  createLocation(req: {
    islandId: number;
    name: string;
    code: string;
    address: string;
    contactPhone?: string | null;
    active?: boolean;
  }): Observable<LocationDto> {
    return this.http.post<LocationDto>('/api/v1/admin/geography/locations', req);
  }
}

