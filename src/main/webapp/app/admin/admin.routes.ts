import { Routes } from '@angular/router';
import { userManagementRoute } from 'app/admin/user-management/user-management.route';
import { systemNotificationManagementRoute } from 'app/admin/system-notification-management/system-notification-management.route';

import { organizationMgmtRoute } from 'app/admin/organization-management/organization-management.route';
import { adminDataExportsRoute } from 'app/admin/admin-data-exports/admin-data-exports.route';
import { adminSbomRoute } from 'app/admin/admin-sbom/admin-sbom.route';

import { LocalCIGuard } from 'app/localci/shared/localci-guard.service';
import { ltiConfigurationRoute } from 'app/admin/lti-configuration/lti-configuration.route';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UpcomingExamsAndExercisesComponent } from 'app/admin/upcoming-exams-and-exercises/upcoming-exams-and-exercises.component';
import { IS_AT_LEAST_ADMIN, IS_AT_LEAST_SUPER_ADMIN } from 'app/shared/constants/authority.constants';
import { AdminContainerComponent } from 'app/admin/admin-container/admin-container.component';

const childRoutes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        redirectTo: 'user-management',
    },
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
        path: 'features',
        loadComponent: () => import('app/admin/features/admin-feature-toggle.component').then((m) => m.AdminFeatureToggleComponent),
        data: {
            pageTitle: 'features.title',
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
        path: 'websocket',
        loadComponent: () => import('app/admin/websocket/websocket-admin.component').then((m) => m.WebsocketAdminComponent),
        data: {
            pageTitle: 'artemisApp.websocketAdmin.title',
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
        path: 'build-overview',
        loadComponent: () => import('app/localci/build-queue/build-overview.component').then((m) => m.BuildOverviewComponent),
        data: {
            pageTitle: 'artemisApp.buildQueue.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'build-overview/:jobId/job-details',
        loadComponent: () => import('app/localci/build-queue/build-job-detail/build-job-detail.component').then((m) => m.BuildJobDetailComponent),
        data: {
            pageTitle: 'artemisApp.buildQueue.detail.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'build-agents',
        loadComponent: () => import('app/localci/build-agent-summary/build-agent-summary.component').then((m) => m.BuildAgentSummaryComponent),
        data: {
            pageTitle: 'artemisApp.buildAgents.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'build-agents/details',
        loadComponent: () => import('app/localci/build-agent-details/build-agent-details.component').then((m) => m.BuildAgentDetailsComponent),
        data: {
            pageTitle: 'artemisApp.buildAgents.title',
        },
        canActivate: [LocalCIGuard],
    },
    {
        path: 'standardized-competencies',
        loadComponent: () => import('app/admin/standardized-competencies/standardized-competency-management.component').then((m) => m.StandardizedCompetencyManagementComponent),
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
        loadComponent: () => import('app/admin/legal/legal-document-update.component').then((m) => m.LegalDocumentUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_ADMIN,
        },
    },
    {
        path: 'imprint',
        loadComponent: () => import('app/admin/legal/legal-document-update.component').then((m) => m.LegalDocumentUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_ADMIN,
        },
    },
    {
        path: 'cleanup-service',
        loadComponent: () => import('app/admin/cleanup-service/cleanup-service.component').then((m) => m.CleanupServiceComponent),
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
    {
        path: 'course-requests',
        loadComponent: () => import('app/admin/course-requests/course-requests.component').then((m) => m.CourseRequestsComponent),
        data: {
            pageTitle: 'artemisApp.courseRequest.admin.title',
        },
    },
    {
        path: 'passkey-management',
        loadComponent: () => import('app/admin/passkey-management/admin-passkey-management.component').then((m) => m.AdminPasskeyManagementComponent),
        data: {
            authorities: IS_AT_LEAST_SUPER_ADMIN,
            pageTitle: 'artemisApp.adminPasskeyManagement.title',
        },
    },
    ...organizationMgmtRoute,
    ...userManagementRoute,
    ...systemNotificationManagementRoute,
    ...ltiConfigurationRoute,
    ...adminDataExportsRoute,
    ...adminSbomRoute,
];

const routes: Routes = [
    {
        path: '',
        component: AdminContainerComponent,
        children: childRoutes,
    },
];

export default routes;
