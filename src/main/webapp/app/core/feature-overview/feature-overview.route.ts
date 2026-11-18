import { Routes } from '@angular/router';

export const featureOverviewRoutes: Routes = [
    {
        path: 'instructors',
        loadComponent: () => import('app/core/feature-overview/feature-overview.component').then((m) => m.FeatureOverviewComponent),
        data: {
            pageTitle: 'featureOverview.instructor.pageTitle',
        },
    },
    {
        path: 'students',
        loadComponent: () => import('app/core/feature-overview/feature-overview.component').then((m) => m.FeatureOverviewComponent),
        data: {
            pageTitle: 'featureOverview.students.pageTitle',
        },
    },
];
