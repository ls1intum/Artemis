import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { logsRoute } from 'app/admin/logs/logs.route';
import { trackerRoute } from 'app/admin/tracker/tracker.route';
import { auditsRoute } from 'app/admin/audits/audits.route';
import { configurationRoute } from 'app/admin/configuration/configuration.route';
import { featureRoute } from 'app/admin/features/features.route';
import { userMgmtRoute1, userMgmtRoute2, userMgmtRoute3, userMgmtRoute4 } from 'app/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/admin/system-notification-management/system-notification-management.route';
import { healthRoute } from 'app/admin/health/health.route';
import { metricsRoute } from 'app/admin/metrics/metrics.route';
import { Authority } from 'app/shared/constants/authority.constants';

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
    featureRoute,
    ...systemNotificationManagementRoute,
];

export const adminState: Routes = [
    {
        path: '',
        data: {
            authorities: [Authority.ADMIN],
        },
        canActivate: [UserRouteAccessService],
        children: ADMIN_ROUTES,
    },
];
