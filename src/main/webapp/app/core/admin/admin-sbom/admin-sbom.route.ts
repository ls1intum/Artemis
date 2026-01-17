import { Route } from '@angular/router';

import { IS_AT_LEAST_ADMIN } from 'app/shared/constants/authority.constants';

export const adminSbomRoute: Route[] = [
    {
        path: 'dependencies',
        loadComponent: () => import('./admin-sbom.component').then((m) => m.AdminSbomComponent),
        data: {
            pageTitle: 'artemisApp.dependencies.title',
            authorities: IS_AT_LEAST_ADMIN,
        },
    },
];
