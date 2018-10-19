import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DropLocationCounter } from 'app/shared/model/drop-location-counter.model';
import { DropLocationCounterService } from './drop-location-counter.service';
import { DropLocationCounterComponent } from './drop-location-counter.component';
import { DropLocationCounterDetailComponent } from './drop-location-counter-detail.component';
import { DropLocationCounterUpdateComponent } from './drop-location-counter-update.component';
import { DropLocationCounterDeletePopupComponent } from './drop-location-counter-delete-dialog.component';
import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';

@Injectable({ providedIn: 'root' })
export class DropLocationCounterResolve implements Resolve<IDropLocationCounter> {
    constructor(private service: DropLocationCounterService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((dropLocationCounter: HttpResponse<DropLocationCounter>) => dropLocationCounter.body));
        }
        return of(new DropLocationCounter());
    }
}

export const dropLocationCounterRoute: Routes = [
    {
        path: 'drop-location-counter',
        component: DropLocationCounterComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocationCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location-counter/:id/view',
        component: DropLocationCounterDetailComponent,
        resolve: {
            dropLocationCounter: DropLocationCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocationCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location-counter/new',
        component: DropLocationCounterUpdateComponent,
        resolve: {
            dropLocationCounter: DropLocationCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocationCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location-counter/:id/edit',
        component: DropLocationCounterUpdateComponent,
        resolve: {
            dropLocationCounter: DropLocationCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocationCounter.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dropLocationCounterPopupRoute: Routes = [
    {
        path: 'drop-location-counter/:id/delete',
        component: DropLocationCounterDeletePopupComponent,
        resolve: {
            dropLocationCounter: DropLocationCounterResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocationCounter.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
