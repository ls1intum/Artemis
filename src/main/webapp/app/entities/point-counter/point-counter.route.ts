import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { PointCounter } from 'app/shared/model/point-counter.model';
import { PointCounterService } from './point-counter.service';
import { PointCounterComponent } from './point-counter.component';
import { PointCounterDetailComponent } from './point-counter-detail.component';
import { PointCounterUpdateComponent } from './point-counter-update.component';
import { PointCounterDeletePopupComponent } from './point-counter-delete-dialog.component';
import { IPointCounter } from 'app/shared/model/point-counter.model';

@Injectable({ providedIn: 'root' })
export class PointCounterResolve implements Resolve<IPointCounter> {
    constructor(private service: PointCounterService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((pointCounter: HttpResponse<PointCounter>) => pointCounter.body));
        }
        return of(new PointCounter());
    }
}

export const pointCounterRoute: Routes = [
    {
        path: 'point-counter',
        component: PointCounterComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.pointCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'point-counter/:id/view',
        component: PointCounterDetailComponent,
        resolve: {
            pointCounter: PointCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.pointCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'point-counter/new',
        component: PointCounterUpdateComponent,
        resolve: {
            pointCounter: PointCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.pointCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'point-counter/:id/edit',
        component: PointCounterUpdateComponent,
        resolve: {
            pointCounter: PointCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.pointCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const pointCounterPopupRoute: Routes = [
    {
        path: 'point-counter/:id/delete',
        component: PointCounterDeletePopupComponent,
        resolve: {
            pointCounter: PointCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.pointCounter.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
