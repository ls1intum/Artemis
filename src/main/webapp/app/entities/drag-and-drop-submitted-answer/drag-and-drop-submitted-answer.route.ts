import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { DragAndDropSubmittedAnswerComponent } from './drag-and-drop-submitted-answer.component';
import { DragAndDropSubmittedAnswerDetailComponent } from './drag-and-drop-submitted-answer-detail.component';
import { DragAndDropSubmittedAnswerPopupComponent } from './drag-and-drop-submitted-answer-dialog.component';
import { DragAndDropSubmittedAnswerDeletePopupComponent } from './drag-and-drop-submitted-answer-delete-dialog.component';

export const dragAndDropSubmittedAnswerRoute: Routes = [
    {
        path: 'drag-and-drop-submitted-answer',
        component: DragAndDropSubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'drag-and-drop-submitted-answer/:id',
        component: DragAndDropSubmittedAnswerDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropSubmittedAnswerPopupRoute: Routes = [
    {
        path: 'drag-and-drop-submitted-answer-new',
        component: DragAndDropSubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-submitted-answer/:id/edit',
        component: DragAndDropSubmittedAnswerPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-submitted-answer/:id/delete',
        component: DragAndDropSubmittedAnswerDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
