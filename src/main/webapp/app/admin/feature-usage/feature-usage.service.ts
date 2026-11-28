import { Service, inject } from '@angular/core';
import { Observable, map, throwError } from 'rxjs';

import { AdminFeatureUsageApi } from 'app/openapi/api/admin-feature-usage-api';
import { FeatureUsageOverviewCallerRoleEnumValues } from 'app/openapi/model/feature-usage-overview';
import { UserFeatureUsageFeatureEnum, UserFeatureUsageFeatureEnumValues } from 'app/openapi/model/user-feature-usage';
import { mapFeatureUsageOverview, mapFeatureUsageTrendPoint } from './feature-usage.mapper';
import { FeatureAdoption, FeatureUsageOverview, FeatureUsageTrendPoint } from './feature-usage.model';

/**
 * Service for fetching the built-in feature usage analysis from the server.
 */
@Service()
export class FeatureUsageService {
    private readonly api = inject(AdminFeatureUsageApi);

    /**
     * Retrieves the usage report for the given window, including the features that saw no usage.
     *
     * @param days length of the window in days
     * @param callerRole restrict the counters to callers whose highest global role is this one; omit for every caller
     */
    getOverview(days: number, callerRole?: string): Observable<FeatureUsageOverview> {
        const role = FeatureUsageOverviewCallerRoleEnumValues.find((value) => value === callerRole);
        if (callerRole && !role) {
            return throwError(() => new Error(`Unsupported feature usage caller role: ${callerRole}`));
        }
        return this.api.getFeatureUsage(days, role).pipe(map(mapFeatureUsageOverview));
    }

    /**
     * Retrieves the daily calls of one feature per interaction, summed over every endpoint that serves it. Days without
     * calls are absent.
     *
     * @param feature the catalogue feature to chart
     * @param days length of the window in days
     * @param callerRole restrict the totals to callers whose highest global role is this one; omit for every caller.
     * Passed through so that charting a role-filtered row keeps that filter instead of silently widening to all callers.
     */
    getFeatureTrend(feature: string, days: number, callerRole?: string): Observable<FeatureUsageTrendPoint[]> {
        const catalogueFeature = UserFeatureUsageFeatureEnumValues.find((value) => value === feature);
        if (feature && !catalogueFeature) {
            return throwError(() => new Error(`Unsupported catalogue feature: ${feature}`));
        }
        return this.getTrend(catalogueFeature, undefined, days, callerRole);
    }

    /**
     * Retrieves the daily calls of individual endpoints per interaction. Days without calls are absent.
     *
     * @param featureIds the inventory rows to chart
     * @param days length of the window in days
     * @param callerRole restrict the totals to callers whose highest global role is this one; omit for every caller
     */
    getEndpointTrend(featureIds: number[], days: number, callerRole?: string): Observable<FeatureUsageTrendPoint[]> {
        return this.getTrend(undefined, featureIds, days, callerRole);
    }

    /**
     * Retrieves how many entities have each optional setting switched on.
     */
    getAdoption(): Observable<FeatureAdoption[]> {
        return this.api.getFeatureAdoption();
    }

    /**
     * Sends the weekly digest email now, so an admin can check delivery without waiting for the next Monday.
     */
    sendDigestEmail(): Observable<void> {
        return this.api.sendFeatureUsageDigestEmail().pipe(map(() => undefined));
    }

    private getTrend(feature: UserFeatureUsageFeatureEnum | undefined, featureIds: number[] | undefined, days: number, callerRole?: string): Observable<FeatureUsageTrendPoint[]> {
        const role = FeatureUsageOverviewCallerRoleEnumValues.find((value) => value === callerRole);
        if (callerRole && !role) {
            return throwError(() => new Error(`Unsupported feature usage caller role: ${callerRole}`));
        }
        return this.api.getFeatureUsageTrend(feature, featureIds, days, role).pipe(map((points) => points.map(mapFeatureUsageTrendPoint)));
    }
}
