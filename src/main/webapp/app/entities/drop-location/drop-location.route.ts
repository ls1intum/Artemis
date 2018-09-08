import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from './drop-location.service';
import { DropLocationComponent } from './drop-location.component';
import { DropLocationDetailComponent } from './drop-location-detail.component';
import { DropLocationUpdateComponent } from './drop-location-update.component';
import { DropLocationDeletePopupComponent } from './drop-location-delete-dialog.component';
import { IDropLocation } from 'app/shared/model/drop-location.model';

@Injectable({ providedIn: 'root' })
export class DropLocationResolve implements Resolve<IDropLocation> {
    constructor(private service: DropLocationService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((dropLocation: HttpResponse<DropLocation>) => dropLocation.body));
        }
        return of(new DropLocation());
    }
}

export const dropLocationRoute: Routes = [
    {
        path: 'drop-location',
        component: DropLocationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location/:id/view',
        component: DropLocationDetailComponent,
        resolve: {
            dropLocation: DropLocationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location/new',
        component: DropLocationUpdateComponent,
        resolve: {
            dropLocation: DropLocationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drop-location/:id/edit',
        component: DropLocationUpdateComponent,
        resolve: {
            dropLocation: DropLocationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dropLocationPopupRoute: Routes = [
    {
        path: 'drop-location/:id/delete',
        component: DropLocationDeletePopupComponent,
        resolve: {
            dropLocation: DropLocationResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
