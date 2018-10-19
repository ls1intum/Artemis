import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';
import { DragAndDropQuestionStatisticService } from './drag-and-drop-question-statistic.service';
import { DragAndDropQuestionStatisticComponent } from './drag-and-drop-question-statistic.component';
import { DragAndDropQuestionStatisticDetailComponent } from './drag-and-drop-question-statistic-detail.component';
import { DragAndDropQuestionStatisticUpdateComponent } from './drag-and-drop-question-statistic-update.component';
import { DragAndDropQuestionStatisticDeletePopupComponent } from './drag-and-drop-question-statistic-delete-dialog.component';
import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropQuestionStatisticResolve implements Resolve<IDragAndDropQuestionStatistic> {
    constructor(private service: DragAndDropQuestionStatisticService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(map((dragAndDropQuestionStatistic: HttpResponse<DragAndDropQuestionStatistic>) => dragAndDropQuestionStatistic.body));
        }
        return of(new DragAndDropQuestionStatistic());
    }
}

export const dragAndDropQuestionStatisticRoute: Routes = [
    {
        path: 'drag-and-drop-question-statistic',
        component: DragAndDropQuestionStatisticComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question-statistic/:id/view',
        component: DragAndDropQuestionStatisticDetailComponent,
        resolve: {
            dragAndDropQuestionStatistic: DragAndDropQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question-statistic/new',
        component: DragAndDropQuestionStatisticUpdateComponent,
        resolve: {
            dragAndDropQuestionStatistic: DragAndDropQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question-statistic/:id/edit',
        component: DragAndDropQuestionStatisticUpdateComponent,
        resolve: {
            dragAndDropQuestionStatistic: DragAndDropQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropQuestionStatisticPopupRoute: Routes = [
    {
        path: 'drag-and-drop-question-statistic/:id/delete',
        component: DragAndDropQuestionStatisticDeletePopupComponent,
        resolve: {
            dragAndDropQuestionStatistic: DragAndDropQuestionStatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestionStatistic.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
