import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { AnswerOptionComponent } from './answer-option.component';
import { AnswerOptionDetailComponent } from './answer-option-detail.component';
import { AnswerOptionPopupComponent } from './answer-option-dialog.component';
import { AnswerOptionDeletePopupComponent } from './answer-option-delete-dialog.component';

export const answerOptionRoute: Routes = [
    {
        path: 'answer-option',
        component: AnswerOptionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'answer-option/:id',
        component: AnswerOptionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const answerOptionPopupRoute: Routes = [
    {
        path: 'answer-option-new',
        component: AnswerOptionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'answer-option/:id/edit',
        component: AnswerOptionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'answer-option/:id/delete',
        component: AnswerOptionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerOption.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
