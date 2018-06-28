import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { MultipleChoiceSubmittedAnswerComponent } from './multiple-choice-submitted-answer.component';
import { MultipleChoiceSubmittedAnswerDetailComponent } from './multiple-choice-submitted-answer-detail.component';
import { MultipleChoiceSubmittedAnswerPopupComponent } from './multiple-choice-submitted-answer-dialog.component';
import {
    MultipleChoiceSubmittedAnswerDeletePopupComponent
} from './multiple-choice-submitted-answer-delete-dialog.component';

export const multipleChoiceSubmittedAnswerRoute: Routes = [
    {
        path: 'multiple-choice-submitted-answer',
        component: MultipleChoiceSubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'multiple-choice-submitted-answer/:id',
        component: MultipleChoiceSubmittedAnswerDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const multipleChoiceSubmittedAnswerPopupRoute: Routes = [
    {
        path: 'multiple-choice-submitted-answer-new',
        component: MultipleChoiceSubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'multiple-choice-submitted-answer/:id/edit',
        component: MultipleChoiceSubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'multiple-choice-submitted-answer/:id/delete',
        component: MultipleChoiceSubmittedAnswerDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
