import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { FeatureAdoption, FeatureUsageOverview, FeatureUsageTrendPoint } from './feature-usage.model';

/**
 * Service for fetching the built-in feature usage analysis from the server.
 */
@Injectable({ providedIn: 'root' })
export class FeatureUsageService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/admin/feature-usage';

    /**
     * Retrieves the usage report for the given window, including the features that saw no usage.
     *
     * @param days length of the window in days
     * @param callerRole restrict the counters to callers whose highest global role is this one; omit for every caller
     */
    getOverview(days: number, callerRole?: string): Observable<FeatureUsageOverview> {
        return this.http.get<FeatureUsageOverview>(this.resourceUrl, { params: callerRole ? { days, callerRole } : { days } });
    }

    /**
     * Retrieves the daily usage of a single feature. Days without usage are absent from the response.
     *
     * @param featureIds the endpoints behind the feature to chart
     * @param days length of the window in days
     * @param callerRole restrict the totals to callers whose highest global role is this one; omit for every caller.
     * Passed through so that charting a role-filtered row keeps that filter instead of silently widening to all callers.
     */
    getTrend(featureIds: number[], days: number, callerRole?: string): Observable<FeatureUsageTrendPoint[]> {
        // repeated rather than joined, because a labelled feature covers several endpoints and the chart sums all of them
        return this.http.get<FeatureUsageTrendPoint[]>(`${this.resourceUrl}/trend`, { params: callerRole ? { featureIds, days, callerRole } : { featureIds, days } });
    }

    /**
     * Retrieves how many entities have each optional feature switched on.
     */
    getAdoption(): Observable<FeatureAdoption[]> {
        return this.http.get<FeatureAdoption[]>(`${this.resourceUrl}/adoption`);
    }

    /**
     * Sends the weekly digest email now, so an admin can check delivery without waiting for the next Monday.
     */
    sendDigestEmail(): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/digest/send-email`, {});
    }
}
