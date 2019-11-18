import { Route } from '@angular/router';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';

export const featureRoute: Route = {
    path: 'feature-toggles',
    component: AdminFeatureToggleComponent,
    data: {
        pageTitle: 'featureToggles.title',
    },
};
