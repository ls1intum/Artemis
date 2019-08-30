import { Route } from '@angular/router';

import { JhiHealthCheckComponent } from 'app/admin';

export const healthRoute: Route = {
    path: 'jhi-health',
    component: JhiHealthCheckComponent,
    data: {
        pageTitle: 'health.title',
    },
};
