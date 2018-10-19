import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { MultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestionStatisticService } from './multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatisticComponent } from './multiple-choice-question-statistic.component';
import { MultipleChoiceQuestionStatisticDetailComponent } from './multiple-choice-question-statistic-detail.component';
import { MultipleChoiceQuestionStatisticUpdateComponent } from './multiple-choice-question-statistic-update.component';
import { MultipleChoiceQuestionStatisticDeletePopupComponent } from './multiple-choice-question-statistic-delete-dialog.component';
import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

@Injectable({ providedIn: 'root' })
export class MultipleChoiceQuestionStatisticResolve implements Resolve<IMultipleChoiceQuestionStatistic> {
    constructor(private service: MultipleChoiceQuestionStatisticService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(
                    map(
                        (multipleChoiceQuestionStatistic: HttpResponse<MultipleChoiceQuestionStatistic>) =>
                            multipleChoiceQuestionStatistic.body
                    )
                );
        }
        return of(new MultipleChoiceQuestionStatistic());
    }
}

export const multipleChoiceQuestionStatisticRoute: Routes = [
    {
        path: 'multiple-choice-question-statistic',
        component: MultipleChoiceQuestionStatisticComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question-statistic/:id/view',
        component: MultipleChoiceQuestionStatisticDetailComponent,
        resolve: {
            multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question-statistic/new',
        component: MultipleChoiceQuestionStatisticUpdateComponent,
        resolve: {
            multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'multiple-choice-question-statistic/:id/edit',
        component: MultipleChoiceQuestionStatisticUpdateComponent,
        resolve: {
            multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const multipleChoiceQuestionStatisticPopupRoute: Routes = [
    {
        path: 'multiple-choice-question-statistic/:id/delete',
        component: MultipleChoiceQuestionStatisticDeletePopupComponent,
        resolve: {
            multipleChoiceQuestionStatistic: MultipleChoiceQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.multipleChoiceQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
