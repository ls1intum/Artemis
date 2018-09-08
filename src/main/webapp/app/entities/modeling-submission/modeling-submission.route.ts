import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ModelingSubmission } from 'app/shared/model/modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';
import { ModelingSubmissionComponent } from './modeling-submission.component';
import { ModelingSubmissionDetailComponent } from './modeling-submission-detail.component';
import { ModelingSubmissionUpdateComponent } from './modeling-submission-update.component';
import { ModelingSubmissionDeletePopupComponent } from './modeling-submission-delete-dialog.component';
import { IModelingSubmission } from 'app/shared/model/modeling-submission.model';

@Injectable({ providedIn: 'root' })
export class ModelingSubmissionResolve implements Resolve<IModelingSubmission> {
    constructor(private service: ModelingSubmissionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((modelingSubmission: HttpResponse<ModelingSubmission>) => modelingSubmission.body));
        }
        return of(new ModelingSubmission());
    }
}

export const modelingSubmissionRoute: Routes = [
    {
        path: 'modeling-submission',
        component: ModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-submission/:id/view',
        component: ModelingSubmissionDetailComponent,
        resolve: {
            modelingSubmission: ModelingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-submission/new',
        component: ModelingSubmissionUpdateComponent,
        resolve: {
            modelingSubmission: ModelingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-submission/:id/edit',
        component: ModelingSubmissionUpdateComponent,
        resolve: {
            modelingSubmission: ModelingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const modelingSubmissionPopupRoute: Routes = [
    {
        path: 'modeling-submission/:id/delete',
        component: ModelingSubmissionDeletePopupComponent,
        resolve: {
            modelingSubmission: ModelingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
