import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import {
    auditsRoute,
    configurationRoute,
    featureRoute,
    healthRoute,
    logsRoute,
    metricsRoute,
    notificationMgmtRoutes1,
    notificationMgmtRoutes2,
    notificationMgmtRoutes3,
    notificationMgmtRoutes4,
    trackerRoute,
    userMgmtRoute1,
    userMgmtRoute2,
    userMgmtRoute3,
    userMgmtRoute4,
} from './';

const ADMIN_ROUTES = [
    auditsRoute,
    configurationRoute,
    healthRoute,
    logsRoute,
    trackerRoute,
    userMgmtRoute1,
    userMgmtRoute2,
    userMgmtRoute3,
    userMgmtRoute4,
    metricsRoute,
    notificationMgmtRoutes1,
    notificationMgmtRoutes2,
    notificationMgmtRoutes3,
    notificationMgmtRoutes4,
    featureRoute,
];

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
