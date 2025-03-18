import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const teamRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('./teams.component').then((m) => m.TeamsComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.team.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':teamId',
        loadComponent: () => import('app/exercise/team/team.component').then((m) => m.TeamComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.team.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
