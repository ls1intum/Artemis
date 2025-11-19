import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_TUTOR, IS_USER } from 'app/shared/constants/authority.constants';

export const teamRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('./teams/teams.component').then((m) => m.TeamsComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.team.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':teamId',
        loadComponent: () => import('app/exercise/team/team.component').then((m) => m.TeamComponent),
        data: {
            authorities: IS_USER,
            pageTitle: 'artemisApp.team.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
