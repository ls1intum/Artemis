import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { FeedbackComponent } from './feedback.component';
import { FeedbackDetailComponent } from './feedback-detail.component';
import { FeedbackPopupComponent } from './feedback-dialog.component';
import { FeedbackDeletePopupComponent } from './feedback-delete-dialog.component';

export const feedbackRoute: Routes = [
    {
        path: 'feedback',
        component: FeedbackComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'feedback/:id',
        component: FeedbackDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const feedbackPopupRoute: Routes = [
    {
        path: 'feedback-new',
        component: FeedbackPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'feedback/:id/edit',
        component: FeedbackPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'feedback/:id/delete',
        component: FeedbackDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
