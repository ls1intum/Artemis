import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { DragItemComponent } from './drag-item.component';
import { DragItemDetailComponent } from './drag-item-detail.component';
import { DragItemPopupComponent } from './drag-item-dialog.component';
import { DragItemDeletePopupComponent } from './drag-item-delete-dialog.component';

export const dragItemRoute: Routes = [
    {
        path: 'drag-item',
        component: DragItemComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'drag-item/:id',
        component: DragItemDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragItemPopupRoute: Routes = [
    {
        path: 'drag-item-new',
        component: DragItemPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-item/:id/edit',
        component: DragItemPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-item/:id/delete',
        component: DragItemDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
