import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { ParticipationComponent } from './participation.component';

export const participationRoute: Routes = [
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
