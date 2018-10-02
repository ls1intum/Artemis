import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { ParticipationComponent } from './participation.component';
import { ParticipationDetailComponent } from './participation-detail.component';
import { ParticipationDeletePopupComponent } from './participation-delete-dialog.component';
import { InstructorDashboardResultPopupComponent } from '../../instructor-dashboard/instructor-dashboard-result-dialog.component';

export const participationRoute: Routes = [
    {
        path: 'participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'participation/:id',
        component: ParticipationDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'exercise/:exerciseId/participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const participationPopupRoute: Routes = [
    {
        path: 'participation/:id/delete',
        component: ParticipationDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'participation/:participationId/result/new',
        component: InstructorDashboardResultPopupComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
