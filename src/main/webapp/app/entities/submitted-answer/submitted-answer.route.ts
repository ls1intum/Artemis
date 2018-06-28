import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { SubmittedAnswerComponent } from './submitted-answer.component';
import { SubmittedAnswerDetailComponent } from './submitted-answer-detail.component';
import { SubmittedAnswerPopupComponent } from './submitted-answer-dialog.component';
import { SubmittedAnswerDeletePopupComponent } from './submitted-answer-delete-dialog.component';

export const submittedAnswerRoute: Routes = [
    {
        path: 'submitted-answer',
        component: SubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'submitted-answer/:id',
        component: SubmittedAnswerDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const submittedAnswerPopupRoute: Routes = [
    {
        path: 'submitted-answer-new',
        component: SubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'submitted-answer/:id/edit',
        component: SubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'submitted-answer/:id/delete',
        component: SubmittedAnswerDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
