import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';
import { DragAndDropAssignmentComponent } from './drag-and-drop-assignment.component';
import { DragAndDropAssignmentDetailComponent } from './drag-and-drop-assignment-detail.component';
import { DragAndDropAssignmentUpdateComponent } from './drag-and-drop-assignment-update.component';
import { DragAndDropAssignmentDeletePopupComponent } from './drag-and-drop-assignment-delete-dialog.component';
import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropAssignmentResolve implements Resolve<IDragAndDropAssignment> {
    constructor(private service: DragAndDropAssignmentService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(map((dragAndDropAssignment: HttpResponse<DragAndDropAssignment>) => dragAndDropAssignment.body));
        }
        return of(new DragAndDropAssignment());
    }
}

export const dragAndDropAssignmentRoute: Routes = [
    {
        path: 'drag-and-drop-assignment',
        component: DragAndDropAssignmentComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-assignment/:id/view',
        component: DragAndDropAssignmentDetailComponent,
        resolve: {
            dragAndDropAssignment: DragAndDropAssignmentResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-assignment/new',
        component: DragAndDropAssignmentUpdateComponent,
        resolve: {
            dragAndDropAssignment: DragAndDropAssignmentResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-assignment/:id/edit',
        component: DragAndDropAssignmentUpdateComponent,
        resolve: {
            dragAndDropAssignment: DragAndDropAssignmentResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropAssignmentPopupRoute: Routes = [
    {
        path: 'drag-and-drop-assignment/:id/delete',
        component: DragAndDropAssignmentDeletePopupComponent,
        resolve: {
            dragAndDropAssignment: DragAndDropAssignmentResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
