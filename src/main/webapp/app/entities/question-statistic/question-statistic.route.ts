import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuestionStatistic } from 'app/shared/model/question-statistic.model';
import { QuestionStatisticService } from './question-statistic.service';
import { QuestionStatisticComponent } from './question-statistic.component';
import { QuestionStatisticDetailComponent } from './question-statistic-detail.component';
import { QuestionStatisticUpdateComponent } from './question-statistic-update.component';
import { QuestionStatisticDeletePopupComponent } from './question-statistic-delete-dialog.component';
import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';

@Injectable({ providedIn: 'root' })
export class QuestionStatisticResolve implements Resolve<IQuestionStatistic> {
    constructor(private service: QuestionStatisticService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((questionStatistic: HttpResponse<QuestionStatistic>) => questionStatistic.body));
        }
        return of(new QuestionStatistic());
    }
}

export const questionStatisticRoute: Routes = [
    {
        path: 'question-statistic',
        component: QuestionStatisticComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.questionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'question-statistic/:id/view',
        component: QuestionStatisticDetailComponent,
        resolve: {
            questionStatistic: QuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.questionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'question-statistic/new',
        component: QuestionStatisticUpdateComponent,
        resolve: {
            questionStatistic: QuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.questionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'question-statistic/:id/edit',
        component: QuestionStatisticUpdateComponent,
        resolve: {
            questionStatistic: QuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.questionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const questionStatisticPopupRoute: Routes = [
    {
        path: 'question-statistic/:id/delete',
        component: QuestionStatisticDeletePopupComponent,
        resolve: {
            questionStatistic: QuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.questionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
