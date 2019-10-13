import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ParticipationComponent } from './participation.component';
import { ExerciseScoresResultResultPopupComponent } from 'app/scores/exercise-scores-result-dialog.component';

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
];

export const participationPopupRoute: Routes = [
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
];
