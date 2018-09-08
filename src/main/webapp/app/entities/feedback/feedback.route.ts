import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { Feedback } from 'app/shared/model/feedback.model';
import { FeedbackService } from './feedback.service';
import { FeedbackComponent } from './feedback.component';
import { FeedbackDetailComponent } from './feedback-detail.component';
import { FeedbackUpdateComponent } from './feedback-update.component';
import { FeedbackDeletePopupComponent } from './feedback-delete-dialog.component';
import { IFeedback } from 'app/shared/model/feedback.model';

@Injectable({ providedIn: 'root' })
export class FeedbackResolve implements Resolve<IFeedback> {
    constructor(private service: FeedbackService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((feedback: HttpResponse<Feedback>) => feedback.body));
        }
        return of(new Feedback());
    }
}

export const feedbackRoute: Routes = [
    {
        path: 'feedback',
        component: FeedbackComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'feedback/:id/view',
        component: FeedbackDetailComponent,
        resolve: {
            feedback: FeedbackResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'feedback/new',
        component: FeedbackUpdateComponent,
        resolve: {
            feedback: FeedbackResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'feedback/:id/edit',
        component: FeedbackUpdateComponent,
        resolve: {
            feedback: FeedbackResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const feedbackPopupRoute: Routes = [
    {
        path: 'feedback/:id/delete',
        component: FeedbackDeletePopupComponent,
        resolve: {
            feedback: FeedbackResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.feedback.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
