import { Routes } from '@angular/router';

export const errorRoute: Routes = [
    {
        path: 'error',
        loadComponent: () => import('./error.component').then((m) => m.ErrorComponent),
        data: {
            authorities: [],
            pageTitle: 'error.title',
        },
    },
    {
        path: 'accessdenied',
        loadComponent: () => import('./error.component').then((m) => m.ErrorComponent),
        data: {
            authorities: [],
            pageTitle: 'error.title',
            error403: true,
        },
    },
];
