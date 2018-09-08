import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';
import { DragAndDropQuestionComponent } from './drag-and-drop-question.component';
import { DragAndDropQuestionDetailComponent } from './drag-and-drop-question-detail.component';
import { DragAndDropQuestionUpdateComponent } from './drag-and-drop-question-update.component';
import { DragAndDropQuestionDeletePopupComponent } from './drag-and-drop-question-delete-dialog.component';
import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropQuestionResolve implements Resolve<IDragAndDropQuestion> {
    constructor(private service: DragAndDropQuestionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((dragAndDropQuestion: HttpResponse<DragAndDropQuestion>) => dragAndDropQuestion.body));
        }
        return of(new DragAndDropQuestion());
    }
}

export const dragAndDropQuestionRoute: Routes = [
    {
        path: 'drag-and-drop-question',
        component: DragAndDropQuestionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question/:id/view',
        component: DragAndDropQuestionDetailComponent,
        resolve: {
            dragAndDropQuestion: DragAndDropQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question/new',
        component: DragAndDropQuestionUpdateComponent,
        resolve: {
            dragAndDropQuestion: DragAndDropQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-question/:id/edit',
        component: DragAndDropQuestionUpdateComponent,
        resolve: {
            dragAndDropQuestion: DragAndDropQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropQuestionPopupRoute: Routes = [
    {
        path: 'drag-and-drop-question/:id/delete',
        component: DragAndDropQuestionDeletePopupComponent,
        resolve: {
            dragAndDropQuestion: DragAndDropQuestionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropQuestion.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
