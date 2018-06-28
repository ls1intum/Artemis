import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { MultipleChoiceQuestionComponent } from './multiple-choice-question.component';
import { MultipleChoiceQuestionDetailComponent } from './multiple-choice-question-detail.component';
import { MultipleChoiceQuestionPopupComponent } from './multiple-choice-question-dialog.component';
import { MultipleChoiceQuestionDeletePopupComponent } from './multiple-choice-question-delete-dialog.component';

export const multipleChoiceQuestionRoute: Routes = [
    {
        path: 'multiple-choice-question',
        component: MultipleChoiceQuestionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'multiple-choice-question/:id',
        component: MultipleChoiceQuestionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const multipleChoiceQuestionPopupRoute: Routes = [
    {
        path: 'multiple-choice-question-new',
        component: MultipleChoiceQuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'multiple-choice-question/:id/edit',
        component: MultipleChoiceQuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'multiple-choice-question/:id/delete',
        component: MultipleChoiceQuestionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
