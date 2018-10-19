import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Statistic } from 'app/shared/model/statistic.model';
import { StatisticService } from './statistic.service';
import { StatisticComponent } from './statistic.component';
import { StatisticDetailComponent } from './statistic-detail.component';
import { StatisticUpdateComponent } from './statistic-update.component';
import { StatisticDeletePopupComponent } from './statistic-delete-dialog.component';
import { IStatistic } from 'app/shared/model/statistic.model';

@Injectable({ providedIn: 'root' })
export class StatisticResolve implements Resolve<IStatistic> {
    constructor(private service: StatisticService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((statistic: HttpResponse<Statistic>) => statistic.body));
        }
        return of(new Statistic());
    }
}

export const statisticRoute: Routes = [
    {
        path: 'statistic',
        component: StatisticComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic/:id/view',
        component: StatisticDetailComponent,
        resolve: {
            statistic: StatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic/new',
        component: StatisticUpdateComponent,
        resolve: {
            statistic: StatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'statistic/:id/edit',
        component: StatisticUpdateComponent,
        resolve: {
            statistic: StatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statistic.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const statisticPopupRoute: Routes = [
    {
        path: 'statistic/:id/delete',
        component: StatisticDeletePopupComponent,
        resolve: {
            statistic: StatisticResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.statistic.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
