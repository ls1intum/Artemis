import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { LtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';
import { LtiOutcomeUrlComponent } from './lti-outcome-url.component';
import { LtiOutcomeUrlDetailComponent } from './lti-outcome-url-detail.component';
import { LtiOutcomeUrlUpdateComponent } from './lti-outcome-url-update.component';
import { LtiOutcomeUrlDeletePopupComponent } from './lti-outcome-url-delete-dialog.component';
import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

@Injectable({ providedIn: 'root' })
export class LtiOutcomeUrlResolve implements Resolve<ILtiOutcomeUrl> {
    constructor(private service: LtiOutcomeUrlService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((ltiOutcomeUrl: HttpResponse<LtiOutcomeUrl>) => ltiOutcomeUrl.body));
        }
        return of(new LtiOutcomeUrl());
    }
}

export const ltiOutcomeUrlRoute: Routes = [
    {
        path: 'lti-outcome-url',
        component: LtiOutcomeUrlComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-outcome-url/:id/view',
        component: LtiOutcomeUrlDetailComponent,
        resolve: {
            ltiOutcomeUrl: LtiOutcomeUrlResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-outcome-url/new',
        component: LtiOutcomeUrlUpdateComponent,
        resolve: {
            ltiOutcomeUrl: LtiOutcomeUrlResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-outcome-url/:id/edit',
        component: LtiOutcomeUrlUpdateComponent,
        resolve: {
            ltiOutcomeUrl: LtiOutcomeUrlResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const ltiOutcomeUrlPopupRoute: Routes = [
    {
        path: 'lti-outcome-url/:id/delete',
        component: LtiOutcomeUrlDeletePopupComponent,
        resolve: {
            ltiOutcomeUrl: LtiOutcomeUrlResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiOutcomeUrl.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
