import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { userManagementRoute } from 'app/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/admin/system-notification-management/system-notification-management.route';
import { Authority } from 'app/shared/constants/authority.constants';
import { upcomingExamsAndExercisesRoute } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.route';

import { organizationMgmtRoute } from 'app/admin/organization-management/organization-management.route';

import { LocalCIGuard } from 'app/localci/localci-guard.service';
import { ltiConfigurationRoute } from 'app/admin/lti-configuration/lti-configuration.route';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

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
                loadComponent: () => import('app/admin/audits/audits.component').then((m) => m.AuditsComponent),
                data: {
                    pageTitle: 'audits.title',
                    defaultSort: 'auditEventDate,desc',
                },
            },
            {
                path: 'configuration',
                loadComponent: () => import('app/admin/configuration/configuration.component').then((m) => m.ConfigurationComponent),
                data: {
                    pageTitle: 'configuration.title',
                },
            },
            {
                path: 'feature-toggles',
                loadComponent: () => import('app/admin/features/admin-feature-toggle.component').then((m) => m.AdminFeatureToggleComponent),
                data: {
                    pageTitle: 'featureToggles.title',
                },
            },
            {
                path: 'health',
                loadComponent: () => import('app/admin/health/health.component').then((m) => m.HealthComponent),
                data: {
                    pageTitle: 'health.title',
                },
            },
            {
                path: 'logs',
                loadComponent: () => import('app/admin/logs/logs.component').then((m) => m.LogsComponent),
                data: {
                    pageTitle: 'logs.title',
                },
            },
            {
                path: 'docs',
                loadComponent: () => import('app/admin/docs/docs.component').then((m) => m.DocsComponent),
                data: {
                    pageTitle: 'global.menu.admin.apidocs',
                },
            },
            {
                path: 'metrics',
                loadComponent: () => import('app/admin/metrics/metrics.component').then((m) => m.MetricsComponent),
                data: {
                    pageTitle: 'metrics.title',
                },
            },
            {
                path: 'user-statistics',
                loadComponent: () => import('app/admin/statistics/statistics.component').then((m) => m.StatisticsComponent),
                data: {
                    pageTitle: 'statistics.title',
                },
            },
            {
                path: 'build-queue',
                loadComponent: () => import('app/localci/build-queue/build-queue.component').then((m) => m.BuildQueueComponent),
                data: {
                    pageTitle: 'artemisApp.buildQueue.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'build-agents',
                loadComponent: () => import('app/localci/build-agents/build-agent-summary/build-agent-summary.component').then((m) => m.BuildAgentSummaryComponent),
                data: {
                    pageTitle: 'artemisApp.buildAgents.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'build-agents/details',
                loadComponent: () =>
                    import('app/localci/build-agents/build-agent-details/build-agent-details/build-agent-details.component').then((m) => m.BuildAgentDetailsComponent),
                data: {
                    pageTitle: 'artemisApp.buildAgents.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'standardized-competencies',
                loadComponent: () =>
                    import('app/admin/standardized-competencies/standardized-competency-management.component').then((m) => m.StandardizedCompetencyManagementComponent),
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
                            import('app/admin/standardized-competencies/import/admin-import-standardized-competencies.component').then(
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
                loadChildren: () => import('./legal/legal-update.module').then((module) => module.LegalUpdateModule),
            },
            {
                path: 'imprint',
                loadChildren: () => import('./legal/legal-update.module').then((module) => module.LegalUpdateModule),
            },
            {
                path: 'iris',
                loadChildren: () =>
                    import('../iris/settings/iris-global-settings-update/iris-global-settings-update-routing.module').then(
                        (module) => module.IrisGlobalSettingsUpdateRoutingModule,
                    ),
            },
            {
                path: 'cleanup-service',
                loadComponent: () => import('app/admin/cleanup-service/cleanup-service.component').then((m) => m.CleanupServiceComponent),
                data: {
                    pageTitle: 'cleanupService.title',
                },
            },
            ...organizationMgmtRoute,
            ...userManagementRoute,
            ...systemNotificationManagementRoute,
            upcomingExamsAndExercisesRoute,
            ...ltiConfigurationRoute,
        ],
    },
];
