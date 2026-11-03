import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IrisAdminDashboardApi } from 'app/openapi/api/iris-admin-dashboard-api';
import { IrisDashboardBreakdownEntry } from 'app/openapi/model/iris-dashboard-breakdown-entry';
import { IrisDashboardOverview } from 'app/openapi/model/iris-dashboard-overview';
import { IrisDashboardAiSelection, IrisDashboardBreakdownDimension } from './iris-dashboard.model';

/** Dashboard feature facade; generated transport keeps the reads cold and cancellable. */
@Service()
export class IrisDashboardService {
    private readonly dashboardApi = inject(IrisAdminDashboardApi);

    getOverview(from: string, to: string, aiSelection: IrisDashboardAiSelection): Observable<IrisDashboardOverview> {
        return this.dashboardApi.getOverview(from, to, aiSelection);
    }

    getBreakdown(from: string, to: string, dimension: IrisDashboardBreakdownDimension, aiSelection: IrisDashboardAiSelection): Observable<IrisDashboardBreakdownEntry[]> {
        return this.dashboardApi.getBreakdown(from, to, dimension, aiSelection);
    }
}
