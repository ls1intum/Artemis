import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { auditsRoute, configurationRoute, healthRoute, logsRoute, metricsRoute, trackerRoute, notificationMgmtRoute } from './';

const ADMIN_ROUTES = [auditsRoute, configurationRoute, healthRoute, logsRoute, trackerRoute, ...notificationMgmtRoute, metricsRoute];

export const adminState: Routes = [
    {
        path: '',
        data: {
            authorities: ['ROLE_ADMIN'],
        },
        canActivate: [UserRouteAccessService],
        children: ADMIN_ROUTES,
    },
];
