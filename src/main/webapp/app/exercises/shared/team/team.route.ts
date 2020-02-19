import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TeamsComponent } from './teams.component';

export const teamRoute: Routes = [
    {
        path: 'exercise/:exerciseId/teams',
        component: TeamsComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.team.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
