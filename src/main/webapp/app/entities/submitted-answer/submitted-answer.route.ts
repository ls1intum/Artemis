import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { SubmittedAnswer } from 'app/shared/model/submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';
import { SubmittedAnswerComponent } from './submitted-answer.component';
import { SubmittedAnswerDetailComponent } from './submitted-answer-detail.component';
import { SubmittedAnswerUpdateComponent } from './submitted-answer-update.component';
import { SubmittedAnswerDeletePopupComponent } from './submitted-answer-delete-dialog.component';
import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';

@Injectable({ providedIn: 'root' })
export class SubmittedAnswerResolve implements Resolve<ISubmittedAnswer> {
    constructor(private service: SubmittedAnswerService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((submittedAnswer: HttpResponse<SubmittedAnswer>) => submittedAnswer.body));
        }
        return of(new SubmittedAnswer());
    }
}

export const submittedAnswerRoute: Routes = [
    {
        path: 'submitted-answer',
        component: SubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'submitted-answer/:id/view',
        component: SubmittedAnswerDetailComponent,
        resolve: {
            submittedAnswer: SubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'submitted-answer/new',
        component: SubmittedAnswerUpdateComponent,
        resolve: {
            submittedAnswer: SubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'submitted-answer/:id/edit',
        component: SubmittedAnswerUpdateComponent,
        resolve: {
            submittedAnswer: SubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const submittedAnswerPopupRoute: Routes = [
    {
        path: 'submitted-answer/:id/delete',
        component: SubmittedAnswerDeletePopupComponent,
        resolve: {
            submittedAnswer: SubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.submittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
