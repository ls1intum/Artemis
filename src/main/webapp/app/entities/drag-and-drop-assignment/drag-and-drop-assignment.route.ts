import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { DragAndDropAssignmentComponent } from './drag-and-drop-assignment.component';
import { DragAndDropAssignmentDetailComponent } from './drag-and-drop-assignment-detail.component';
import { DragAndDropAssignmentPopupComponent } from './drag-and-drop-assignment-dialog.component';
import { DragAndDropAssignmentDeletePopupComponent } from './drag-and-drop-assignment-delete-dialog.component';

export const dragAndDropAssignmentRoute: Routes = [
    {
        path: 'drag-and-drop-assignment',
        component: DragAndDropAssignmentComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'drag-and-drop-assignment/:id',
        component: DragAndDropAssignmentDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropAssignmentPopupRoute: Routes = [
    {
        path: 'drag-and-drop-assignment-new',
        component: DragAndDropAssignmentPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-assignment/:id/edit',
        component: DragAndDropAssignmentPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-assignment/:id/delete',
        component: DragAndDropAssignmentDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropAssignment.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
