import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { LtiUserId } from 'app/shared/model/lti-user-id.model';
import { LtiUserIdService } from './lti-user-id.service';
import { LtiUserIdComponent } from './lti-user-id.component';
import { LtiUserIdDetailComponent } from './lti-user-id-detail.component';
import { LtiUserIdUpdateComponent } from './lti-user-id-update.component';
import { LtiUserIdDeletePopupComponent } from './lti-user-id-delete-dialog.component';
import { ILtiUserId } from 'app/shared/model/lti-user-id.model';

@Injectable({ providedIn: 'root' })
export class LtiUserIdResolve implements Resolve<ILtiUserId> {
    constructor(private service: LtiUserIdService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((ltiUserId: HttpResponse<LtiUserId>) => ltiUserId.body));
        }
        return of(new LtiUserId());
    }
}

export const ltiUserIdRoute: Routes = [
    {
        path: 'lti-user-id',
        component: LtiUserIdComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-user-id/:id/view',
        component: LtiUserIdDetailComponent,
        resolve: {
            ltiUserId: LtiUserIdResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-user-id/new',
        component: LtiUserIdUpdateComponent,
        resolve: {
            ltiUserId: LtiUserIdResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'lti-user-id/:id/edit',
        component: LtiUserIdUpdateComponent,
        resolve: {
            ltiUserId: LtiUserIdResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const ltiUserIdPopupRoute: Routes = [
    {
        path: 'lti-user-id/:id/delete',
        component: LtiUserIdDeletePopupComponent,
        resolve: {
            ltiUserId: LtiUserIdResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.ltiUserId.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
