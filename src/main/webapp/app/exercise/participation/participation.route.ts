import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

export const routes: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/participations',
            loadComponent: () => import('./participation.component').then((m) => m.ParticipationComponent),
            data: {
                authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
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
                authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
