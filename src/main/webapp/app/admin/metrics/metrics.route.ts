import { Route } from '@angular/router';

import { JhiMetricsMonitoringComponent } from 'app/admin';

export const metricsRoute: Route = {
    path: 'jhi-metrics',
    component: JhiMetricsMonitoringComponent,
    data: {
        pageTitle: 'metrics.title',
    },
};
