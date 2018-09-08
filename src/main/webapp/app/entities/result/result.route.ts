import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Result } from 'app/shared/model/result.model';
import { ResultService } from './result.service';
import { ResultComponent } from './result.component';
import { ResultDetailComponent } from './result-detail.component';
import { ResultUpdateComponent } from './result-update.component';
import { ResultDeletePopupComponent } from './result-delete-dialog.component';
import { IResult } from 'app/shared/model/result.model';

@Injectable({ providedIn: 'root' })
export class ResultResolve implements Resolve<IResult> {
    constructor(private service: ResultService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((result: HttpResponse<Result>) => result.body));
        }
        return of(new Result());
    }
}

export const resultRoute: Routes = [
    {
        path: 'result',
        component: ResultComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'result/:id/view',
        component: ResultDetailComponent,
        resolve: {
            result: ResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'result/new',
        component: ResultUpdateComponent,
        resolve: {
            result: ResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'result/:id/edit',
        component: ResultUpdateComponent,
        resolve: {
            result: ResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const resultPopupRoute: Routes = [
    {
        path: 'result/:id/delete',
        component: ResultDeletePopupComponent,
        resolve: {
            result: ResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
