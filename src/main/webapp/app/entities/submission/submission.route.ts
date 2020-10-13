import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, Routes, Router } from '@angular/router';
import { Observable, of, EMPTY } from 'rxjs';
import { flatMap } from 'rxjs/operators';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ISubmission, Submission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';
import { SubmissionComponent } from './submission.component';
import { SubmissionDetailComponent } from './submission-detail.component';
import { SubmissionUpdateComponent } from './submission-update.component';

@Injectable({ providedIn: 'root' })
export class SubmissionResolve implements Resolve<ISubmission> {
    constructor(private service: SubmissionService, private router: Router) {}

    resolve(route: ActivatedRouteSnapshot): Observable<ISubmission> | Observable<never> {
        const id = route.params['id'];
        if (id) {
            return this.service.find(id).pipe(
                flatMap((submission: HttpResponse<Submission>) => {
                    if (submission.body) {
                        return of(submission.body);
                    } else {
                        this.router.navigate(['404']);
                        return EMPTY;
                    }
                }),
            );
        }
        return of(new Submission());
    }
}

export const submissionRoute: Routes = [
    {
        path: '',
        component: SubmissionComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.submission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/view',
        component: SubmissionDetailComponent,
        resolve: {
            submission: SubmissionResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.submission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: SubmissionUpdateComponent,
        resolve: {
            submission: SubmissionResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.submission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/edit',
        component: SubmissionUpdateComponent,
        resolve: {
            submission: SubmissionResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.submission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
