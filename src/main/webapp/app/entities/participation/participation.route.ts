import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { ParticipationComponent } from './participation.component';
import { ParticipationDeletePopupComponent } from './participation-delete-dialog.component';
import { ExerciseScoresResultResultPopupComponent } from 'app/scores/exercise-scores-result-dialog.component';
import { ParticipationCleanupBuildPlanPopupComponent } from 'app/entities/participation/participation-cleanup-build-plan-dialog.component';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';
import { ParticipationSubmissionDeletePopupComponent } from 'app/entities/participation-submission/participation-submission-delete-dialog.component';

export const participationRoute: Routes = [
    {
        path: 'participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercise/:exerciseId/participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'participant/:participationId/submissions',
        component: ParticipationSubmissionComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
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
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'participation/:id/cleanupBuildPlan',
        component: ParticipationCleanupBuildPlanPopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'participation/:participationId/result/new',
        component: ExerciseScoresResultResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
    {
        path: 'participant/submission/:id/delete',
        component: ParticipationSubmissionDeletePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
