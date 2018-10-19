import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';
import { DragAndDropMappingService } from './drag-and-drop-mapping.service';
import { DragAndDropMappingComponent } from './drag-and-drop-mapping.component';
import { DragAndDropMappingDetailComponent } from './drag-and-drop-mapping-detail.component';
import { DragAndDropMappingUpdateComponent } from './drag-and-drop-mapping-update.component';
import { DragAndDropMappingDeletePopupComponent } from './drag-and-drop-mapping-delete-dialog.component';
import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropMappingResolve implements Resolve<IDragAndDropMapping> {
    constructor(private service: DragAndDropMappingService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((dragAndDropMapping: HttpResponse<DragAndDropMapping>) => dragAndDropMapping.body));
        }
        return of(new DragAndDropMapping());
    }
}

export const dragAndDropMappingRoute: Routes = [
    {
        path: 'drag-and-drop-mapping',
        component: DragAndDropMappingComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropMapping.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-mapping/:id/view',
        component: DragAndDropMappingDetailComponent,
        resolve: {
            dragAndDropMapping: DragAndDropMappingResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropMapping.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-mapping/new',
        component: DragAndDropMappingUpdateComponent,
        resolve: {
            dragAndDropMapping: DragAndDropMappingResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropMapping.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-mapping/:id/edit',
        component: DragAndDropMappingUpdateComponent,
        resolve: {
            dragAndDropMapping: DragAndDropMappingResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropMapping.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropMappingPopupRoute: Routes = [
    {
        path: 'drag-and-drop-mapping/:id/delete',
        component: DragAndDropMappingDeletePopupComponent,
        resolve: {
            dragAndDropMapping: DragAndDropMappingResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropMapping.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
