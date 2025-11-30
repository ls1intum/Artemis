import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';

export const routes: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/participations',
            loadComponent: () => import('./participation.component').then((m) => m.ParticipationComponent),
            data: {
                authorities: IS_AT_LEAST_TUTOR,
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/participations/:participationId/submissions',
            loadComponent: () => import('../participation-submission/participation-submission.component').then((m) => m.ParticipationSubmissionComponent),
            data: {
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
