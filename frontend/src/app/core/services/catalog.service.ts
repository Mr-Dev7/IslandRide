import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  IslandDto,
  LocationDto,
  PageResponse,
  PublicVehicleDetailDto,
  PublicVehicleSummaryDto,
  VehicleAvailabilityCalendarDto,
  VehicleCategoryDto,
  VehicleDocumentTypeDto,
  VehicleSearchFilter,
} from '../models/vehicle.models';

@Injectable({
  providedIn: 'root',
})
export class CatalogService {
  private readonly http = inject(HttpClient);

  getIslands(): Observable<IslandDto[]> {
    return this.http.get<IslandDto[]>('/api/v1/locations/islands');
  }

  getHubs(islandId?: number | null, islandCode?: string | null): Observable<LocationDto[]> {
    let params = new HttpParams();
    if (islandId != null) {
      params = params.set('islandId', islandId.toString());
    }
    if (islandCode) {
      params = params.set('islandCode', islandCode);
    }
    return this.http.get<LocationDto[]>('/api/v1/locations/hubs', { params });
  }

  getCategories(): Observable<VehicleCategoryDto[]> {
    return this.http.get<VehicleCategoryDto[]>('/api/v1/categories');
  }

  getDocumentTypes(): Observable<VehicleDocumentTypeDto[]> {
    return this.http.get<VehicleDocumentTypeDto[]>('/api/v1/document-types');
  }

  searchVehicles(filter: VehicleSearchFilter): Observable<PageResponse<PublicVehicleSummaryDto>> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<PageResponse<PublicVehicleSummaryDto>>('/api/v1/vehicles', { params });
  }

  getVehicleDetail(publicId: string): Observable<PublicVehicleDetailDto> {
    return this.http.get<PublicVehicleDetailDto>(`/api/v1/vehicles/${publicId}`);
  }

  getVehicleAvailability(
    publicId: string,
    from?: string,
    to?: string
  ): Observable<VehicleAvailabilityCalendarDto> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<VehicleAvailabilityCalendarDto>(`/api/v1/vehicles/${publicId}/availability`, {
      params,
    });
  }
}

