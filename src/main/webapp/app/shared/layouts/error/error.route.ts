import { Routes } from '@angular/router';

import { ErrorComponent } from './error.component';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';

export const errorRoute: Routes = [
    {
        path: 'error',
        component: ErrorComponent,
        data: {
            authorities: [],
            pageTitle: 'error.title',
        },
    },
    {
        path: 'accessdenied',
        component: ErrorComponent,
        data: {
            authorities: [],
            pageTitle: 'error.title',
            error403: true,
        },
    },
    {
        path: 'orionOutdated',
        component: OrionOutdatedComponent,
        data: {
            authorities: [],
            pageTitle: 'Outdated Orion Version',
        },
    },
];
