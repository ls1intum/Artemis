import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    IrisDashboardBreakdownDimension,
    IrisDashboardBreakdownEntry,
    IrisDashboardConfig,
    IrisDashboardMetric,
    IrisDashboardOverview,
    IrisDashboardTimeSeries,
} from './iris-dashboard.model';

@Injectable({ providedIn: 'root' })
export class IrisDashboardService {
    private http = inject(HttpClient);

    private readonly baseUrl = 'api/iris/admin/dashboard';

    getOverview(from: string, to: string, chatMode?: string): Observable<IrisDashboardOverview> {
        let params = new HttpParams().set('from', from).set('to', to);
        if (chatMode) {
            params = params.set('chatMode', chatMode);
        }
        return this.http.get<IrisDashboardOverview>(`${this.baseUrl}/overview`, { params });
    }

    getTimeSeries(from: string, to: string, span: string, metric: IrisDashboardMetric): Observable<IrisDashboardTimeSeries> {
        const params = new HttpParams().set('from', from).set('to', to).set('span', span).set('metric', metric);
        return this.http.get<IrisDashboardTimeSeries>(`${this.baseUrl}/time-series`, { params });
    }

    getBreakdown(from: string, to: string, dimension: IrisDashboardBreakdownDimension): Observable<IrisDashboardBreakdownEntry[]> {
        const params = new HttpParams().set('from', from).set('to', to).set('dimension', dimension);
        return this.http.get<IrisDashboardBreakdownEntry[]>(`${this.baseUrl}/breakdown`, { params });
    }

    getConfig(): Observable<IrisDashboardConfig> {
        return this.http.get<IrisDashboardConfig>(`${this.baseUrl}/config`);
    }
}
