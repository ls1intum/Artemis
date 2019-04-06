import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { ParticipationComponent } from './participation.component';
import { ParticipationDeletePopupComponent } from './participation-delete-dialog.component';
import { InstructorDashboardResultPopupComponent } from 'app/dashboard/exercise-dashboard-result-dialog.component';
import { ParticipationCleanupBuildPlanPopupComponent } from 'app/entities/participation/participation-cleanup-build-plan-dialog.component';

export const participationRoute: Routes = [
    {
        path: 'participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercise/:exerciseId/participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const participationPopupRoute: Routes = [
    {
        path: 'participation/:id/delete',
        component: ParticipationDeletePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'participation/:id/cleanupBuildPlan',
        component: ParticipationCleanupBuildPlanPopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'participation/:participationId/result/new',
        component: InstructorDashboardResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
