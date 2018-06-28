import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { DropLocationComponent } from './drop-location.component';
import { DropLocationDetailComponent } from './drop-location-detail.component';
import { DropLocationPopupComponent } from './drop-location-dialog.component';
import { DropLocationDeletePopupComponent } from './drop-location-delete-dialog.component';

export const dropLocationRoute: Routes = [
    {
        path: 'drop-location',
        component: DropLocationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'drop-location/:id',
        component: DropLocationDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dropLocationPopupRoute: Routes = [
    {
        path: 'drop-location-new',
        component: DropLocationPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drop-location/:id/edit',
        component: DropLocationPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drop-location/:id/delete',
        component: DropLocationDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dropLocation.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
