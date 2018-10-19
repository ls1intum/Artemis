import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatisticCounter } from 'app/shared/model/statistic-counter.model';
import { StatisticCounterService } from './statistic-counter.service';
import { StatisticCounterComponent } from './statistic-counter.component';
import { StatisticCounterDetailComponent } from './statistic-counter-detail.component';
import { StatisticCounterUpdateComponent } from './statistic-counter-update.component';
import { StatisticCounterDeletePopupComponent } from './statistic-counter-delete-dialog.component';
import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';

@Injectable({ providedIn: 'root' })
export class StatisticCounterResolve implements Resolve<IStatisticCounter> {
    constructor(private service: StatisticCounterService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((statisticCounter: HttpResponse<StatisticCounter>) => statisticCounter.body));
        }
        return of(new StatisticCounter());
    }
}

export const statisticCounterRoute: Routes = [
    {
        path: 'statistic-counter',
        component: StatisticCounterComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statisticCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic-counter/:id/view',
        component: StatisticCounterDetailComponent,
        resolve: {
            statisticCounter: StatisticCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statisticCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic-counter/new',
        component: StatisticCounterUpdateComponent,
        resolve: {
            statisticCounter: StatisticCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statisticCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic-counter/:id/edit',
        component: StatisticCounterUpdateComponent,
        resolve: {
            statisticCounter: StatisticCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statisticCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const statisticCounterPopupRoute: Routes = [
    {
        path: 'statistic-counter/:id/delete',
        component: StatisticCounterDeletePopupComponent,
        resolve: {
            statisticCounter: StatisticCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statisticCounter.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
