import { Routes } from '@angular/router';

export const featureOverviewRoutes: Routes = [
    {
        path: 'instructors',
        loadComponent: () => import('app/feature-overview/feature-overview.component').then((m) => m.FeatureOverviewComponent),
        data: {
            pageTitle: 'featureOverview.instructor.pageTitle',
        },
    },
    {
        path: 'students',
        loadComponent: () => import('app/feature-overview/feature-overview.component').then((m) => m.FeatureOverviewComponent),
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
