import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';
import { DragAndDropSubmittedAnswerComponent } from './drag-and-drop-submitted-answer.component';
import { DragAndDropSubmittedAnswerDetailComponent } from './drag-and-drop-submitted-answer-detail.component';
import { DragAndDropSubmittedAnswerUpdateComponent } from './drag-and-drop-submitted-answer-update.component';
import { DragAndDropSubmittedAnswerDeletePopupComponent } from './drag-and-drop-submitted-answer-delete-dialog.component';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropSubmittedAnswerResolve implements Resolve<IDragAndDropSubmittedAnswer> {
    constructor(private service: DragAndDropSubmittedAnswerService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(map((dragAndDropSubmittedAnswer: HttpResponse<DragAndDropSubmittedAnswer>) => dragAndDropSubmittedAnswer.body));
        }
        return of(new DragAndDropSubmittedAnswer());
    }
}

export const dragAndDropSubmittedAnswerRoute: Routes = [
    {
        path: 'drag-and-drop-submitted-answer',
        component: DragAndDropSubmittedAnswerComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-submitted-answer/:id/view',
        component: DragAndDropSubmittedAnswerDetailComponent,
        resolve: {
            dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-submitted-answer/new',
        component: DragAndDropSubmittedAnswerUpdateComponent,
        resolve: {
            dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'drag-and-drop-submitted-answer/:id/edit',
        component: DragAndDropSubmittedAnswerUpdateComponent,
        resolve: {
            dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const dragAndDropSubmittedAnswerPopupRoute: Routes = [
    {
        path: 'drag-and-drop-submitted-answer/:id/delete',
        component: DragAndDropSubmittedAnswerDeletePopupComponent,
        resolve: {
            dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswerResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.dragAndDropSubmittedAnswer.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
