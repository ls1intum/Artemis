import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { QuizSubmissionComponent } from './quiz-submission.component';
import { QuizSubmissionDetailComponent } from './quiz-submission-detail.component';
import { QuizSubmissionPopupComponent } from './quiz-submission-dialog.component';
import { QuizSubmissionDeletePopupComponent } from './quiz-submission-delete-dialog.component';

export const quizSubmissionRoute: Routes = [
    {
        path: 'quiz-submission',
        component: QuizSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'quiz-submission/:id',
        component: QuizSubmissionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizSubmissionPopupRoute: Routes = [
    {
        path: 'quiz-submission-new',
        component: QuizSubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'quiz-submission/:id/edit',
        component: QuizSubmissionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'quiz-submission/:id/delete',
        component: QuizSubmissionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
