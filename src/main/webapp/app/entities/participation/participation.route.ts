import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ParticipationComponent } from './participation.component';
import { ParticipationDetailComponent } from './participation-detail.component';
import { ParticipationPopupComponent } from './participation-dialog.component';
import { ParticipationDeletePopupComponent } from './participation-delete-dialog.component';

export const participationRoute: Routes = [
    {
        path: 'participation',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'participation/:id',
        component: ParticipationDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const participationPopupRoute: Routes = [
    {
        path: 'participation-new',
        component: ParticipationPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'participation/:id/edit',
        component: ParticipationPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'participation/:id/delete',
        component: ParticipationDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.participation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
