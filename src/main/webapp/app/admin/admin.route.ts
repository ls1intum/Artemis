import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { userManagementRoute } from 'app/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/admin/system-notification-management/system-notification-management.route';
import { Authority } from 'app/shared/constants/authority.constants';
import { upcomingExamsAndExercisesRoute } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.route';
import { AuditsComponent } from 'app/admin/audits/audits.component';
import { ConfigurationComponent } from 'app/admin/configuration/configuration.component';
import { AdminFeatureToggleComponent } from 'app/admin/features/admin-feature-toggle.component';
import { HealthComponent } from 'app/admin/health/health.component';
import { LogsComponent } from 'app/admin/logs/logs.component';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';
import { DocsComponent } from 'app/admin/docs/docs.component';
import { organizationMgmtRoute } from 'app/admin/organization-management/organization-management.route';
import { MetricsComponent } from 'app/admin/metrics/metrics.component';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { LocalCIGuard } from 'app/localci/localci-guard.service';
import { ltiConfigurationRoute } from 'app/admin/lti-configuration/lti-configuration.route';
import { BuildAgentSummaryComponent } from 'app/localci/build-agents/build-agent-summary/build-agent-summary.component';
import { StandardizedCompetencyManagementComponent } from 'app/admin/standardized-competencies/standardized-competency-management.component';
import { BuildAgentDetailsComponent } from 'app/localci/build-agents/build-agent-details/build-agent-details/build-agent-details.component';
import { AdminImportStandardizedCompetenciesComponent } from 'app/admin/standardized-competencies/import/admin-import-standardized-competencies.component';
import { CleanupServiceComponent } from 'app/admin/cleanup-service/cleanup-service.component';
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
                component: AuditsComponent,
                data: {
                    pageTitle: 'audits.title',
                    defaultSort: 'auditEventDate,desc',
                },
            },
            {
                path: 'configuration',
                component: ConfigurationComponent,
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
                path: 'health',
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
                path: 'docs',
                component: DocsComponent,
                data: {
                    pageTitle: 'global.menu.admin.apidocs',
                },
            },
            {
                path: 'metrics',
                component: MetricsComponent,
                data: {
                    pageTitle: 'metrics.title',
                },
            },
            {
                path: 'user-statistics',
                component: StatisticsComponent,
                data: {
                    pageTitle: 'statistics.title',
                },
            },
            {
                path: 'build-queue',
                component: BuildQueueComponent,
                data: {
                    pageTitle: 'artemisApp.buildQueue.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'build-agents',
                component: BuildAgentSummaryComponent,
                data: {
                    pageTitle: 'artemisApp.buildAgents.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'build-agents/details',
                component: BuildAgentDetailsComponent,
                data: {
                    pageTitle: 'artemisApp.buildAgents.title',
                },
                canActivate: [LocalCIGuard],
            },
            {
                path: 'standardized-competencies',
                component: StandardizedCompetencyManagementComponent,
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
                        component: AdminImportStandardizedCompetenciesComponent,
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
                component: CleanupServiceComponent,
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
