import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizSubmission } from 'app/shared/model/quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';
import { QuizSubmissionComponent } from './quiz-submission.component';
import { QuizSubmissionDetailComponent } from './quiz-submission-detail.component';
import { QuizSubmissionUpdateComponent } from './quiz-submission-update.component';
import { QuizSubmissionDeletePopupComponent } from './quiz-submission-delete-dialog.component';
import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';

@Injectable({ providedIn: 'root' })
export class QuizSubmissionResolve implements Resolve<IQuizSubmission> {
    constructor(private service: QuizSubmissionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((quizSubmission: HttpResponse<QuizSubmission>) => quizSubmission.body));
        }
        return of(new QuizSubmission());
    }
}

export const quizSubmissionRoute: Routes = [
    {
        path: 'quiz-submission',
        component: QuizSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-submission/:id/view',
        component: QuizSubmissionDetailComponent,
        resolve: {
            quizSubmission: QuizSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-submission/new',
        component: QuizSubmissionUpdateComponent,
        resolve: {
            quizSubmission: QuizSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-submission/:id/edit',
        component: QuizSubmissionUpdateComponent,
        resolve: {
            quizSubmission: QuizSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizSubmissionPopupRoute: Routes = [
    {
        path: 'quiz-submission/:id/delete',
        component: QuizSubmissionDeletePopupComponent,
        resolve: {
            quizSubmission: QuizSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
