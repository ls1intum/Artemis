import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { AnswerCounter } from 'app/shared/model/answer-counter.model';
import { AnswerCounterService } from './answer-counter.service';
import { AnswerCounterComponent } from './answer-counter.component';
import { AnswerCounterDetailComponent } from './answer-counter-detail.component';
import { AnswerCounterUpdateComponent } from './answer-counter-update.component';
import { AnswerCounterDeletePopupComponent } from './answer-counter-delete-dialog.component';
import { IAnswerCounter } from 'app/shared/model/answer-counter.model';

@Injectable({ providedIn: 'root' })
export class AnswerCounterResolve implements Resolve<IAnswerCounter> {
    constructor(private service: AnswerCounterService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((answerCounter: HttpResponse<AnswerCounter>) => answerCounter.body));
        }
        return of(new AnswerCounter());
    }
}

export const answerCounterRoute: Routes = [
    {
        path: 'answer-counter',
        component: AnswerCounterComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-counter/:id/view',
        component: AnswerCounterDetailComponent,
        resolve: {
            answerCounter: AnswerCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-counter/new',
        component: AnswerCounterUpdateComponent,
        resolve: {
            answerCounter: AnswerCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'answer-counter/:id/edit',
        component: AnswerCounterUpdateComponent,
        resolve: {
            answerCounter: AnswerCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const answerCounterPopupRoute: Routes = [
    {
        path: 'answer-counter/:id/delete',
        component: AnswerCounterDeletePopupComponent,
        resolve: {
            answerCounter: AnswerCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.answerCounter.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
