import { Route } from '@angular/router';

import { JhiTrackerComponent } from 'app/admin';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';

export const featureRoute: Route = {
    path: 'jhi-features',
    component: AdminFeatureToggleComponent,
    data: {
        pageTitle: 'features.title',
    },
};
