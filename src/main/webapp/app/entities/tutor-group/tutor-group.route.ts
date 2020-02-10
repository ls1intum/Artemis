import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { TutorGroupService } from './tutor-group.service';
import { TutorGroupComponent } from './tutor-group.component';
import { TutorGroupDetailComponent } from './tutor-group-detail.component';
import { TutorGroupUpdateComponent } from './tutor-group-update.component';
import { TutorGroupDeletePopupComponent } from './tutor-group-delete-dialog.component';
import { TutorGroup } from 'app/entities/tutor-group';

@Injectable({ providedIn: 'root' })
export class TutorGroupResolve implements Resolve<TutorGroup> {
    constructor(private service: TutorGroupService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<TutorGroup> {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<TutorGroup>) => response.ok),
                map((tutorGroup: HttpResponse<TutorGroup>) => tutorGroup.body!),
            );
        }
        return of(new TutorGroup());
    }
}

export const tutorGroupRoute: Routes = [
    {
        path: '',
        component: TutorGroupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.tutorGroup.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/view',
        component: TutorGroupDetailComponent,
        resolve: {
            tutorGroup: TutorGroupResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.tutorGroup.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: TutorGroupUpdateComponent,
        resolve: {
            tutorGroup: TutorGroupResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.tutorGroup.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/edit',
        component: TutorGroupUpdateComponent,
        resolve: {
            tutorGroup: TutorGroupResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.tutorGroup.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const tutorGroupPopupRoute: Routes = [
    {
        path: ':id/delete',
        component: TutorGroupDeletePopupComponent,
        resolve: {
            tutorGroup: TutorGroupResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.tutorGroup.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
