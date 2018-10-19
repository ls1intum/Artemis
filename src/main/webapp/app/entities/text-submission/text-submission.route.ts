import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { TextSubmission } from 'app/shared/model/text-submission.model';
import { TextSubmissionService } from './text-submission.service';
import { TextSubmissionComponent } from './text-submission.component';
import { TextSubmissionDetailComponent } from './text-submission-detail.component';
import { TextSubmissionUpdateComponent } from './text-submission-update.component';
import { TextSubmissionDeletePopupComponent } from './text-submission-delete-dialog.component';
import { ITextSubmission } from 'app/shared/model/text-submission.model';

@Injectable({ providedIn: 'root' })
export class TextSubmissionResolve implements Resolve<ITextSubmission> {
    constructor(private service: TextSubmissionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((textSubmission: HttpResponse<TextSubmission>) => textSubmission.body));
        }
        return of(new TextSubmission());
    }
}

export const textSubmissionRoute: Routes = [
    {
        path: 'text-submission',
        component: TextSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-submission/:id/view',
        component: TextSubmissionDetailComponent,
        resolve: {
            textSubmission: TextSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-submission/new',
        component: TextSubmissionUpdateComponent,
        resolve: {
            textSubmission: TextSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-submission/:id/edit',
        component: TextSubmissionUpdateComponent,
        resolve: {
            textSubmission: TextSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const textSubmissionPopupRoute: Routes = [
    {
        path: 'text-submission/:id/delete',
        component: TextSubmissionDeletePopupComponent,
        resolve: {
            textSubmission: TextSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
