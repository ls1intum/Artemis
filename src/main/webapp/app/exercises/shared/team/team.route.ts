import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TeamsComponent } from './teams.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

export const teamRoute: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/teams',
            component: TeamsComponent,
            data: {
                authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.team.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/teams/:teamId',
            component: TeamComponent,
            data: {
                authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.team.detail.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
