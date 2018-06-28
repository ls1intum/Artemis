import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { QuestionComponent } from './question.component';
import { QuestionDetailComponent } from './question-detail.component';
import { QuestionPopupComponent } from './question-dialog.component';
import { QuestionDeletePopupComponent } from './question-delete-dialog.component';

export const questionRoute: Routes = [
    {
        path: 'question',
        component: QuestionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.question.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'question/:id',
        component: QuestionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.question.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const questionPopupRoute: Routes = [
    {
        path: 'question-new',
        component: QuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.question.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'question/:id/edit',
        component: QuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.question.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'question/:id/delete',
        component: QuestionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.question.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
