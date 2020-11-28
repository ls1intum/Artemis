import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { userMgmtRoute } from 'app/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/admin/system-notification-management/system-notification-management.route';
import { Authority } from 'app/shared/constants/authority.constants';
import { upcomingExamsAndExercisesRoute } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.route';
import { AuditsComponent } from 'app/admin/audits/audits.component';
import { JhiConfigurationComponent } from 'app/admin/configuration/configuration.component';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';
import { HealthComponent } from 'app/admin/health/health.component';
import { LogsComponent } from 'app/admin/logs/logs.component';
import { JhiMetricsMonitoringComponent } from 'app/admin/metrics/metrics.component';
import { JhiTrackerComponent } from 'app/admin/tracker/tracker.component';

export const adminState: Routes = [
    {
        path: '',
        data: {
            authorities: [Authority.ADMIN],
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'audits',
                component: AuditsComponent,
                data: {
                    pageTitle: 'audits.title',
                    defaultSort: 'auditEventDate,desc',
                },
            },
            {
                path: 'jhi-configuration',
                component: JhiConfigurationComponent,
                data: {
                    pageTitle: 'configuration.title',
                },
            },
            {
                path: 'feature-toggles',
                component: AdminFeatureToggleComponent,
                data: {
                    pageTitle: 'featureToggles.title',
                },
            },
            {
                path: 'jhi-health',
                component: HealthComponent,
                data: {
                    pageTitle: 'health.title',
                },
            },
            {
                path: 'logs',
                component: LogsComponent,
                data: {
                    pageTitle: 'logs.title',
                },
            },
            {
                path: 'jhi-metrics',
                component: JhiMetricsMonitoringComponent,
                data: {
                    pageTitle: 'metrics.title',
                },
            },
            {
                path: 'jhi-tracker',
                component: JhiTrackerComponent,
                data: {
                    pageTitle: 'tracker.title',
                },
            },
            userMgmtRoute,
            systemNotificationManagementRoute,
            upcomingExamsAndExercisesRoute,
        ],
    },
];
