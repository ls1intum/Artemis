import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { logsRoute } from 'app/admin/logs/logs.route';
import { trackerRoute } from 'app/admin/tracker/tracker.route';
import { auditsRoute } from 'app/admin/audits/audits.route';
import { configurationRoute } from 'app/admin/configuration/configuration.route';
import { featureRoute } from 'app/admin/features/features.route';
import { userMgmtRoute1, userMgmtRoute2, userMgmtRoute3, userMgmtRoute4 } from 'app/admin/user-management/user-management.route';
import {
    notificationMgmtRoutes1,
    notificationMgmtRoutes2,
    notificationMgmtRoutes3,
    notificationMgmtRoutes4,
} from 'app/admin/notification-management/notification-management.route';
import { healthRoute } from 'app/admin/health/health.route';
import { metricsRoute } from 'app/admin/metrics/metrics.route';

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
