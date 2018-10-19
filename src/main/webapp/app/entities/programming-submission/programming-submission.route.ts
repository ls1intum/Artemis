import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProgrammingSubmission } from 'app/shared/model/programming-submission.model';
import { ProgrammingSubmissionService } from './programming-submission.service';
import { ProgrammingSubmissionComponent } from './programming-submission.component';
import { ProgrammingSubmissionDetailComponent } from './programming-submission-detail.component';
import { ProgrammingSubmissionUpdateComponent } from './programming-submission-update.component';
import { ProgrammingSubmissionDeletePopupComponent } from './programming-submission-delete-dialog.component';
import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingSubmissionResolve implements Resolve<IProgrammingSubmission> {
    constructor(private service: ProgrammingSubmissionService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service
                .find(id)
                .pipe(map((programmingSubmission: HttpResponse<ProgrammingSubmission>) => programmingSubmission.body));
        }
        return of(new ProgrammingSubmission());
    }
}

export const programmingSubmissionRoute: Routes = [
    {
        path: 'programming-submission',
        component: ProgrammingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-submission/:id/view',
        component: ProgrammingSubmissionDetailComponent,
        resolve: {
            programmingSubmission: ProgrammingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-submission/new',
        component: ProgrammingSubmissionUpdateComponent,
        resolve: {
            programmingSubmission: ProgrammingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-submission/:id/edit',
        component: ProgrammingSubmissionUpdateComponent,
        resolve: {
            programmingSubmission: ProgrammingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const programmingSubmissionPopupRoute: Routes = [
    {
        path: 'programming-submission/:id/delete',
        component: ProgrammingSubmissionDeletePopupComponent,
        resolve: {
            programmingSubmission: ProgrammingSubmissionResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingSubmission.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
