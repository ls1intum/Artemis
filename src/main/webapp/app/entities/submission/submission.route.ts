import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { SubmissionComponent } from './submission.component';
import { SubmissionDetailComponent } from './submission-detail.component';
import { SubmissionPopupComponent } from './submission-dialog.component';
import { SubmissionDeletePopupComponent } from './submission-delete-dialog.component';

export const submissionRoute: Routes = [
    {
        path: 'submission',
        component: SubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'submission/:id',
        component: SubmissionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const submissionPopupRoute: Routes = [
    {
        path: 'submission-new',
        component: SubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'submission/:id/edit',
        component: SubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'submission/:id/delete',
        component: SubmissionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
