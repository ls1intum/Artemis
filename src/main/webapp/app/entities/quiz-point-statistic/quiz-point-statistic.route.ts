import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { QuizPointStatisticService } from './quiz-point-statistic.service';
import { QuizPointStatisticComponent } from './quiz-point-statistic.component';
import { QuizPointStatisticDetailComponent } from './quiz-point-statistic-detail.component';
import { QuizPointStatisticUpdateComponent } from './quiz-point-statistic-update.component';
import { QuizPointStatisticDeletePopupComponent } from './quiz-point-statistic-delete-dialog.component';
import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

@Injectable({ providedIn: 'root' })
export class QuizPointStatisticResolve implements Resolve<IQuizPointStatistic> {
    constructor(private service: QuizPointStatisticService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((quizPointStatistic: HttpResponse<QuizPointStatistic>) => quizPointStatistic.body));
        }
        return of(new QuizPointStatistic());
    }
}

export const quizPointStatisticRoute: Routes = [
    {
        path: 'quiz-point-statistic',
        component: QuizPointStatisticComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-point-statistic/:id/view',
        component: QuizPointStatisticDetailComponent,
        resolve: {
            quizPointStatistic: QuizPointStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-point-statistic/new',
        component: QuizPointStatisticUpdateComponent,
        resolve: {
            quizPointStatistic: QuizPointStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-point-statistic/:id/edit',
        component: QuizPointStatisticUpdateComponent,
        resolve: {
            quizPointStatistic: QuizPointStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizPointStatisticPopupRoute: Routes = [
    {
        path: 'quiz-point-statistic/:id/delete',
        component: QuizPointStatisticDeletePopupComponent,
        resolve: {
            quizPointStatistic: QuizPointStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizPointStatistic.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
