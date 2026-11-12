import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CommissionDetailDto,
  PartnerFinancialOverviewDto
} from '../models/booking.models';

@Injectable({
  providedIn: 'root'
})
export class PartnerFinancialService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/partners/financials';

  getFinancialOverview(businessId?: number): Observable<PartnerFinancialOverviewDto> {
    let params = new HttpParams();
    if (businessId != null) {
      params = params.set('businessId', businessId.toString());
    }
    return this.http.get<PartnerFinancialOverviewDto>(`${this.baseUrl}/overview`, { params });
  }

  getCommissions(businessId?: number): Observable<CommissionDetailDto[]> {
    let params = new HttpParams();
    if (businessId != null) {
      params = params.set('businessId', businessId.toString());
    }
    return this.http.get<CommissionDetailDto[]>(`${this.baseUrl}/commissions`, { params });
  }
}
