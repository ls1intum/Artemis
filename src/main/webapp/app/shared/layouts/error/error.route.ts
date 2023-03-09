import { Routes } from '@angular/router';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ErrorComponent } from './error.component';

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
        path: 'orion-outdated',
        component: OrionOutdatedComponent,
        data: {
            authorities: [],
            pageTitle: 'Outdated Orion Version',
        },
    },
];
