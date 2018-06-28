import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { LtiUserIdComponent } from './lti-user-id.component';
import { LtiUserIdDetailComponent } from './lti-user-id-detail.component';
import { LtiUserIdPopupComponent } from './lti-user-id-dialog.component';
import { LtiUserIdDeletePopupComponent } from './lti-user-id-delete-dialog.component';

export const ltiUserIdRoute: Routes = [
    {
        path: 'lti-user-id',
        component: LtiUserIdComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'lti-user-id/:id',
        component: LtiUserIdDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const ltiUserIdPopupRoute: Routes = [
    {
        path: 'lti-user-id-new',
        component: LtiUserIdPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'lti-user-id/:id/edit',
        component: LtiUserIdPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'lti-user-id/:id/delete',
        component: LtiUserIdDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
