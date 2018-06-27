import { Routes } from '@angular/router';

import { auditsRoute, configurationRoute, healthRoute, logsRoute, metricsRoute, trackerRoute, userDialogRoute, userMgmtRoute } from './';

import { UserRouteAccessService } from '../shared';

const ADMIN_ROUTES = [
    auditsRoute,
    configurationRoute,
    healthRoute,
    logsRoute,
    trackerRoute,
    ...userMgmtRoute,
    metricsRoute
];

export const adminState: Routes = [{
    path: '',
    data: {
        authorities: ['ROLE_ADMIN']
    },
    canActivate: [UserRouteAccessService],
    children: ADMIN_ROUTES
},
    ...userDialogRoute
];
