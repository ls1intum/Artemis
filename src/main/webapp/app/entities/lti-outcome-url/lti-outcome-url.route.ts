import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { LtiOutcomeUrlComponent } from './lti-outcome-url.component';
import { LtiOutcomeUrlDetailComponent } from './lti-outcome-url-detail.component';
import { LtiOutcomeUrlPopupComponent } from './lti-outcome-url-dialog.component';
import { LtiOutcomeUrlDeletePopupComponent } from './lti-outcome-url-delete-dialog.component';

export const ltiOutcomeUrlRoute: Routes = [
    {
        path: 'lti-outcome-url',
        component: LtiOutcomeUrlComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'lti-outcome-url/:id',
        component: LtiOutcomeUrlDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const ltiOutcomeUrlPopupRoute: Routes = [
    {
        path: 'lti-outcome-url-new',
        component: LtiOutcomeUrlPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'lti-outcome-url/:id/edit',
        component: LtiOutcomeUrlPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'lti-outcome-url/:id/delete',
        component: LtiOutcomeUrlDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
