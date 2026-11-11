import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AvailabilityBlockDto,
  CreateAvailabilityBlockRequest,
  PageResponse,
  PartnerInventorySummaryDto,
  PartnerVehicleDto,
  PricingRuleDto,
  SaveVehiclePricingRequest,
  SaveVehicleRequest,
  VehicleAvailabilityCalendarDto,
  VehicleDocumentDto,
  VehicleImageDto,
  VehiclePricingOverviewDto,
  VehiclePricingPlanDto,
  VehicleStatus,
  PartnerBusinessLocationDto,
  UpdatePartnerLocationsRequest,
} from '../models/vehicle.models';

@Injectable({
  providedIn: 'root',
})
export class PartnerInventoryService {
  private readonly http = inject(HttpClient);

  getSummary(businessPublicId?: string): Observable<PartnerInventorySummaryDto> {
    let params = new HttpParams();
    if (businessPublicId) params = params.set('businessPublicId', businessPublicId);
    return this.http.get<PartnerInventorySummaryDto>('/api/v1/partners/vehicles/summary', { params });
  }

  listVehicles(options: {
    businessPublicId?: string;
    status?: VehicleStatus | '';
    categoryId?: number | null;
    islandId?: number | null;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<PartnerVehicleDto>> {
    let params = new HttpParams();
    Object.entries(options).forEach(([key, val]) => {
      if (val !== undefined && val !== null && val !== '') {
        params = params.set(key, String(val));
      }
    });
    return this.http.get<PageResponse<PartnerVehicleDto>>('/api/v1/partners/vehicles', { params });
  }

  getVehicle(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.get<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}`);
  }

  createVehicle(req: SaveVehicleRequest): Observable<PartnerVehicleDto> {
    return this.http.post<PartnerVehicleDto>('/api/v1/partners/vehicles', req);
  }

  updateVehicle(publicId: string, req: SaveVehicleRequest): Observable<PartnerVehicleDto> {
    return this.http.put<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}`, req);
  }

  submitForReview(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.post<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}/submit`, {});
  }

  pauseVehicle(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.post<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}/pause`, {});
  }

  resumeVehicle(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.post<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}/resume`, {});
  }

  markMaintenance(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.post<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}/maintenance`, {});
  }

  archiveVehicle(publicId: string): Observable<PartnerVehicleDto> {
    return this.http.delete<PartnerVehicleDto>(`/api/v1/partners/vehicles/${publicId}`);
  }

  getPricing(publicId: string): Observable<VehiclePricingOverviewDto> {
    return this.http.get<VehiclePricingOverviewDto>(`/api/v1/partners/vehicles/${publicId}/pricing`);
  }

  savePricing(publicId: string, req: SaveVehiclePricingRequest): Observable<VehiclePricingPlanDto> {
    return this.http.put<VehiclePricingPlanDto>(`/api/v1/partners/vehicles/${publicId}/pricing`, req);
  }

  addPricingRule(
    publicId: string,
    rule: {
      ruleName: string;
      ruleType: 'DURATION_DISCOUNT' | 'WEEKEND_SURGE' | 'EARLY_BIRD';
      minDays: number;
      adjustmentType: 'PERCENTAGE' | 'FIXED_AMOUNT';
      adjustmentValue: number;
    }
  ): Observable<PricingRuleDto> {
    return this.http.post<PricingRuleDto>(`/api/v1/partners/vehicles/${publicId}/pricing/rules`, rule);
  }

  deactivatePricingRule(publicId: string, ruleId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/partners/vehicles/${publicId}/pricing/rules/${ruleId}`);
  }

  getAvailability(publicId: string, from?: string, to?: string): Observable<VehicleAvailabilityCalendarDto> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<VehicleAvailabilityCalendarDto>(
      `/api/v1/partners/vehicles/${publicId}/availability`,
      { params }
    );
  }

  listUpcomingBlocks(businessPublicId?: string): Observable<AvailabilityBlockDto[]> {
    let params = new HttpParams();
    if (businessPublicId) params = params.set('businessPublicId', businessPublicId);
    return this.http.get<AvailabilityBlockDto[]>('/api/v1/partners/calendar/blocks', { params });
  }

  createBlock(publicId: string, req: CreateAvailabilityBlockRequest): Observable<AvailabilityBlockDto> {
    return this.http.post<AvailabilityBlockDto>(
      `/api/v1/partners/vehicles/${publicId}/availability/blocks`,
      req
    );
  }

  removeBlock(publicId: string, blockPublicId: string): Observable<AvailabilityBlockDto> {
    return this.http.delete<AvailabilityBlockDto>(
      `/api/v1/partners/vehicles/${publicId}/availability/blocks/${blockPublicId}`
    );
  }

  uploadImages(publicId: string, files: File[]): Observable<VehicleImageDto[]> {
    const form = new FormData();
    files.forEach((f) => form.append('files', f, f.name));
    return this.http.post<VehicleImageDto[]>(`/api/v1/partners/vehicles/${publicId}/images`, form);
  }

  setPrimaryImage(publicId: string, imagePublicId: string): Observable<VehicleImageDto[]> {
    return this.http.put<VehicleImageDto[]>(
      `/api/v1/partners/vehicles/${publicId}/images/${imagePublicId}/primary`,
      {}
    );
  }

  reorderImages(publicId: string, imagePublicIds: string[]): Observable<VehicleImageDto[]> {
    return this.http.put<VehicleImageDto[]>(`/api/v1/partners/vehicles/${publicId}/images/reorder`, {
      imagePublicIds,
    });
  }

  deleteImage(publicId: string, imagePublicId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/partners/vehicles/${publicId}/images/${imagePublicId}`);
  }

  uploadDocument(
    publicId: string,
    payload: {
      documentTypeCode: string;
      documentNumber?: string;
      issueDate?: string;
      expiryDate?: string;
      file: File;
    }
  ): Observable<VehicleDocumentDto> {
    const form = new FormData();
    form.append('documentTypeCode', payload.documentTypeCode);
    if (payload.documentNumber) form.append('documentNumber', payload.documentNumber);
    if (payload.issueDate) form.append('issueDate', payload.issueDate);
    if (payload.expiryDate) form.append('expiryDate', payload.expiryDate);
    form.append('file', payload.file, payload.file.name);
    return this.http.post<VehicleDocumentDto>(`/api/v1/partners/vehicles/${publicId}/documents`, form);
  }

  listExpiringDocuments(withinDays = 30): Observable<VehicleDocumentDto[]> {
    return this.http.get<VehicleDocumentDto[]>('/api/v1/partners/documents/expiring', {
      params: new HttpParams().set('withinDays', withinDays.toString()),
    });
  }

  downloadPrivateFile(url: string): Observable<Blob> {
    return this.http.get(url, { responseType: 'blob' });
  }

  getBusinessLocations(businessPublicId?: string): Observable<PartnerBusinessLocationDto[]> {
    let params = new HttpParams();
    if (businessPublicId) params = params.set('businessPublicId', businessPublicId);
    return this.http.get<PartnerBusinessLocationDto[]>('/api/v1/partners/locations', { params });
  }

  updateBusinessLocations(req: UpdatePartnerLocationsRequest): Observable<PartnerBusinessLocationDto[]> {
    return this.http.put<PartnerBusinessLocationDto[]>('/api/v1/partners/locations', req);
  }
}
