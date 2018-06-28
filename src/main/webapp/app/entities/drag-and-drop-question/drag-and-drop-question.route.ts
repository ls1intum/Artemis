import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { DragAndDropQuestionComponent } from './drag-and-drop-question.component';
import { DragAndDropQuestionDetailComponent } from './drag-and-drop-question-detail.component';
import { DragAndDropQuestionPopupComponent } from './drag-and-drop-question-dialog.component';
import { DragAndDropQuestionDeletePopupComponent } from './drag-and-drop-question-delete-dialog.component';

export const dragAndDropQuestionRoute: Routes = [
    {
        path: 'drag-and-drop-question',
        component: DragAndDropQuestionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'drag-and-drop-question/:id',
        component: DragAndDropQuestionDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropQuestionPopupRoute: Routes = [
    {
        path: 'drag-and-drop-question-new',
        component: DragAndDropQuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-question/:id/edit',
        component: DragAndDropQuestionPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'drag-and-drop-question/:id/delete',
        component: DragAndDropQuestionDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
