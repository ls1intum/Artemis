import { Routes } from '@angular/router';
import { FeatureOverviewComponent } from 'app/feature-overview/feature-overview.component';

export const featureOverviewRoutes: Routes = [
    {
        path: 'instructors',
        component: FeatureOverviewComponent,
        data: {
            pageTitle: 'featureOverview.instructor.pageTitle',
        },
    },
    {
        path: 'students',
        component: FeatureOverviewComponent,
        data: {
            pageTitle: 'featureOverview.students.pageTitle',
        },
    },
];

const FEATURE_OVERVIEW_ROUTES = [...featureOverviewRoutes];

export const featureOverviewState: Routes = [
    {
        path: '',
        children: FEATURE_OVERVIEW_ROUTES,
    },
];
