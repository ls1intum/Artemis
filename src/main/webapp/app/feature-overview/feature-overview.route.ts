import { Routes } from '@angular/router';

export const routes: Routes = [
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
