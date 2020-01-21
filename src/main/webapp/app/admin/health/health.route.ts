import { Route } from '@angular/router';

import { HealthComponent } from './health.component';

export const healthRoute: Route = {
    path: 'jhi-health',
    component: HealthComponent,
    data: {
        pageTitle: 'health.title',
    },
};
