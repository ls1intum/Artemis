import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ModelingSubmissionComponent } from './modeling-submission.component';
import { ModelingSubmissionDetailComponent } from './modeling-submission-detail.component';
import { ModelingSubmissionPopupComponent } from './modeling-submission-dialog.component';
import { ModelingSubmissionDeletePopupComponent } from './modeling-submission-delete-dialog.component';

export const modelingSubmissionRoute: Routes = [
    {
        path: 'modeling-submission',
        component: ModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'modeling-submission/:id',
        component: ModelingSubmissionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const modelingSubmissionPopupRoute: Routes = [
    {
        path: 'modeling-submission-new',
        component: ModelingSubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'modeling-submission/:id/edit',
        component: ModelingSubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'modeling-submission/:id/delete',
        component: ModelingSubmissionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
