import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TeamsComponent } from './teams.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const teamRoute: Routes = [
    {
        path: ':courseId/exercises/:exerciseId/teams',
        component: TeamsComponent,
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.team.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/exercises/:exerciseId/teams/:teamId',
        component: TeamComponent,
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.team.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
