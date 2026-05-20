import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
    IrisDashboardBreakdownDimension,
    IrisDashboardBreakdownEntry,
    IrisDashboardConfig,
    IrisDashboardMetric,
    IrisDashboardOverview,
    IrisDashboardQuery,
    IrisDashboardSpan,
    IrisDashboardTimeSeries,
} from './iris-dashboard.model';

@Injectable({ providedIn: 'root' })
export class IrisDashboardService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/iris/admin/dashboard';

    getOverview(query: IrisDashboardQuery): Observable<IrisDashboardOverview> {
        return this.http.get<IrisDashboardOverview>(`${this.resourceUrl}/overview`, { params: this.queryParams(query) });
    }

    getTimeSeries(query: IrisDashboardQuery, span: IrisDashboardSpan, metric: IrisDashboardMetric): Observable<IrisDashboardTimeSeries> {
        return this.http.get<IrisDashboardTimeSeries>(`${this.resourceUrl}/time-series`, { params: this.queryParams(query).set('span', span).set('metric', metric) });
    }

    getBreakdown(query: IrisDashboardQuery, dimension: IrisDashboardBreakdownDimension): Observable<IrisDashboardBreakdownEntry[]> {
        return this.http.get<IrisDashboardBreakdownEntry[]>(`${this.resourceUrl}/breakdown`, { params: this.queryParams(query).set('dimension', dimension) });
    }

    getConfig(): Observable<IrisDashboardConfig> {
        return this.http.get<IrisDashboardConfig>(`${this.resourceUrl}/config`);
    }

    private queryParams(query: IrisDashboardQuery): HttpParams {
        let params = new HttpParams().set('from', query.from.toISOString()).set('to', query.to.toISOString());
        if (query.chatMode) {
            params = params.set('chatMode', query.chatMode);
        }
        return params;
    }
}
