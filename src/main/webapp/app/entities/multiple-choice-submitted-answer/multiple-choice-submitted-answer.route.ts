import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { MultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswerComponent } from './multiple-choice-submitted-answer.component';
import { MultipleChoiceSubmittedAnswerDetailComponent } from './multiple-choice-submitted-answer-detail.component';
import { MultipleChoiceSubmittedAnswerUpdateComponent } from './multiple-choice-submitted-answer-update.component';
import { MultipleChoiceSubmittedAnswerDeletePopupComponent } from './multiple-choice-submitted-answer-delete-dialog.component';
import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

@Injectable({ providedIn: 'root' })
export class MultipleChoiceSubmittedAnswerResolve implements Resolve<IMultipleChoiceSubmittedAnswer> {
    constructor(private service: MultipleChoiceSubmittedAnswerService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(
                    map((multipleChoiceSubmittedAnswer: HttpResponse<MultipleChoiceSubmittedAnswer>) => multipleChoiceSubmittedAnswer.body)
                );
        }
        return of(new MultipleChoiceSubmittedAnswer());
    }
}

export const multipleChoiceSubmittedAnswerRoute: Routes = [
    {
        path: 'multiple-choice-submitted-answer',
        component: MultipleChoiceSubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-submitted-answer/:id/view',
        component: MultipleChoiceSubmittedAnswerDetailComponent,
        resolve: {
            multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-submitted-answer/new',
        component: MultipleChoiceSubmittedAnswerUpdateComponent,
        resolve: {
            multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-submitted-answer/:id/edit',
        component: MultipleChoiceSubmittedAnswerUpdateComponent,
        resolve: {
            multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const multipleChoiceSubmittedAnswerPopupRoute: Routes = [
    {
        path: 'multiple-choice-submitted-answer/:id/delete',
        component: MultipleChoiceSubmittedAnswerDeletePopupComponent,
        resolve: {
            multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
