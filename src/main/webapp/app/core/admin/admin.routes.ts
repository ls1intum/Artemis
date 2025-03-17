import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { userManagementRoute } from 'app/core/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/core/admin/system-notification-management/system-notification-management.route';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';
import { Authority } from 'app/shared/constants/authority.constants';

import { organizationMgmtRoute } from 'app/core/admin/organization-management/organization-management.route';

import { LocalCIGuard } from 'app/buildagent/shared/localci-guard.service';
import { ltiConfigurationRoute } from 'app/core/admin/lti-configuration/lti-configuration.route';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UpcomingExamsAndExercisesComponent } from 'app/core/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';

const routes: Routes = [
    {
        path: 'audits',
        loadComponent: () => import('app/core/admin/audits/audits.component').then((m) => m.AuditsComponent),
        data: {
            pageTitle: 'audits.title',
            defaultSort: 'auditEventDate,desc',
        },
    },
    {
        path: 'configuration',
        loadComponent: () => import('app/core/admin/configuration/configuration.component').then((m) => m.ConfigurationComponent),
        data: {
            pageTitle: 'configuration.title',
        },
    },
    {
        path: 'feature-toggles',
        loadComponent: () => import('app/core/admin/features/admin-feature-toggle.component').then((m) => m.AdminFeatureToggleComponent),
        data: {
            pageTitle: 'featureToggles.title',
        },
    },
    {
        path: 'health',
        loadComponent: () => import('app/core/admin/health/health.component').then((m) => m.HealthComponent),
        data: {
            pageTitle: 'health.title',
        },
    },
    {
        path: 'logs',
        loadComponent: () => import('app/core/admin/logs/logs.component').then((m) => m.LogsComponent),
        data: {
            pageTitle: 'logs.title',
        },
    },
    {
        path: 'docs',
        loadComponent: () => import('app/core/admin/docs/docs.component').then((m) => m.DocsComponent),
        data: {
            pageTitle: 'global.menu.admin.apidocs',
        },
    },
    {
        path: 'metrics',
        loadComponent: () => import('app/core/admin/metrics/metrics.component').then((m) => m.MetricsComponent),
        data: {
            pageTitle: 'metrics.title',
        },
    },
    {
        path: 'user-statistics',
        loadComponent: () => import('app/core/admin/statistics/statistics.component').then((m) => m.StatisticsComponent),
        data: {
            pageTitle: 'statistics.title',
        },
    },
    {
        path: 'build-queue',
        loadComponent: () => import('app/buildagent/build-queue/build-queue.component').then((m) => m.BuildQueueComponent),
        data: {
            pageTitle: 'artemisApp.buildQueue.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'build-agents',
        loadComponent: () => import('app/buildagent/build-agent-summary/build-agent-summary.component').then((m) => m.BuildAgentSummaryComponent),
        data: {
            pageTitle: 'artemisApp.buildAgents.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'build-agents/details',
        loadComponent: () => import('app/buildagent/build-agent-details/build-agent-details.component').then((m) => m.BuildAgentDetailsComponent),
        data: {
            pageTitle: 'artemisApp.buildAgents.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'standardized-competencies',
        loadComponent: () =>
            import('app/core/admin/standardized-competencies/standardized-competency-management.component').then((m) => m.StandardizedCompetencyManagementComponent),
        data: {
            pageTitle: 'artemisApp.standardizedCompetency.title',
        },
        canDeactivate: [PendingChangesGuard],
    },
    {
        // Create a new path without a component defined to prevent the StandardizedCompetencyManagementComponent from being always rendered
        path: 'standardized-competencies',
        data: {
            pageTitle: 'artemisApp.standardizedCompetency.title',
        },
        children: [
            {
                path: 'import',
                loadComponent: () =>
                    import('app/core/admin/standardized-competencies/import/admin-import-standardized-competencies.component').then(
                        (m) => m.AdminImportStandardizedCompetenciesComponent,
                    ),
                data: {
                    pageTitle: 'artemisApp.standardizedCompetency.import.title',
                },
            },
        ],
    },
    {
        path: 'privacy-statement',
        loadComponent: () => import('app/core/admin/legal/legal-document-update.component').then((m) => m.LegalDocumentUpdateComponent),
        data: {
            authorities: [Authority.ADMIN],
        },
    },
    {
        path: 'imprint',
        loadComponent: () => import('app/core/admin/legal/legal-document-update.component').then((m) => m.LegalDocumentUpdateComponent),
        data: {
            authorities: [Authority.ADMIN],
        },
    },
    {
        path: 'iris',
        loadComponent: () => import('app/iris/manage/settings/iris-global-settings-update/iris-global-settings-update.component').then((m) => m.IrisGlobalSettingsUpdateComponent),
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.iris.settings.title.global',
        },
        canActivate: [UserRouteAccessService, IrisGuard],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'cleanup-service',
        loadComponent: () => import('app/core/admin/cleanup-service/cleanup-service.component').then((m) => m.CleanupServiceComponent),
        data: {
            pageTitle: 'cleanupService.title',
        },
    },
    {
        path: 'upcoming-exams-and-exercises',
        component: UpcomingExamsAndExercisesComponent,
        data: {
            pageTitle: 'artemisApp.upcomingExamsAndExercises.upcomingExamsAndExercises',
        },
    },
    ...organizationMgmtRoute,
    ...userManagementRoute,
    ...systemNotificationManagementRoute,
    ...ltiConfigurationRoute,
];

export default routes;
