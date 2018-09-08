import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragItem } from 'app/shared/model/drag-item.model';
import { DragItemService } from './drag-item.service';
import { DragItemComponent } from './drag-item.component';
import { DragItemDetailComponent } from './drag-item-detail.component';
import { DragItemUpdateComponent } from './drag-item-update.component';
import { DragItemDeletePopupComponent } from './drag-item-delete-dialog.component';
import { IDragItem } from 'app/shared/model/drag-item.model';

@Injectable({ providedIn: 'root' })
export class DragItemResolve implements Resolve<IDragItem> {
    constructor(private service: DragItemService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((dragItem: HttpResponse<DragItem>) => dragItem.body));
        }
        return of(new DragItem());
    }
}

export const dragItemRoute: Routes = [
    {
        path: 'drag-item',
        component: DragItemComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-item/:id/view',
        component: DragItemDetailComponent,
        resolve: {
            dragItem: DragItemResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-item/new',
        component: DragItemUpdateComponent,
        resolve: {
            dragItem: DragItemResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-item/:id/edit',
        component: DragItemUpdateComponent,
        resolve: {
            dragItem: DragItemResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragItemPopupRoute: Routes = [
    {
        path: 'drag-item/:id/delete',
        component: DragItemDeletePopupComponent,
        resolve: {
            dragItem: DragItemResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragItem.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
