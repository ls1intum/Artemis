import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { MultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';
import { MultipleChoiceQuestionComponent } from './multiple-choice-question.component';
import { MultipleChoiceQuestionDetailComponent } from './multiple-choice-question-detail.component';
import { MultipleChoiceQuestionUpdateComponent } from './multiple-choice-question-update.component';
import { MultipleChoiceQuestionDeletePopupComponent } from './multiple-choice-question-delete-dialog.component';
import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

@Injectable({ providedIn: 'root' })
export class MultipleChoiceQuestionResolve implements Resolve<IMultipleChoiceQuestion> {
    constructor(private service: MultipleChoiceQuestionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(map((multipleChoiceQuestion: HttpResponse<MultipleChoiceQuestion>) => multipleChoiceQuestion.body));
        }
        return of(new MultipleChoiceQuestion());
    }
}

export const multipleChoiceQuestionRoute: Routes = [
    {
        path: 'multiple-choice-question',
        component: MultipleChoiceQuestionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question/:id/view',
        component: MultipleChoiceQuestionDetailComponent,
        resolve: {
            multipleChoiceQuestion: MultipleChoiceQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question/new',
        component: MultipleChoiceQuestionUpdateComponent,
        resolve: {
            multipleChoiceQuestion: MultipleChoiceQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question/:id/edit',
        component: MultipleChoiceQuestionUpdateComponent,
        resolve: {
            multipleChoiceQuestion: MultipleChoiceQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const multipleChoiceQuestionPopupRoute: Routes = [
    {
        path: 'multiple-choice-question/:id/delete',
        component: MultipleChoiceQuestionDeletePopupComponent,
        resolve: {
            multipleChoiceQuestion: MultipleChoiceQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
