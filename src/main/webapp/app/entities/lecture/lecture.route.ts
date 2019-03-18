import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { LectureService } from './lecture.service';
import { LectureComponent } from './lecture.component';
import { LectureDetailComponent } from './lecture-detail.component';
import { LectureUpdateComponent } from './lecture-update.component';
import { LectureDeletePopupComponent } from './lecture-delete-dialog.component';
import { Lecture } from 'app/entities/lecture';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    constructor(private service: LectureService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Lecture> {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<Lecture>) => response.ok),
                map((lecture: HttpResponse<Lecture>) => lecture.body)
            );
        }
        return of(new Lecture());
    }
}

export const lectureRoute: Routes = [
    {
        path: '',
        component: LectureComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.lecture.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: ':id/view',
        component: LectureDetailComponent,
        resolve: {
            lecture: LectureResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.lecture.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'new',
        component: LectureUpdateComponent,
        resolve: {
            lecture: LectureResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.lecture.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: ':id/edit',
        component: LectureUpdateComponent,
        resolve: {
            lecture: LectureResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.lecture.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const lecturePopupRoute: Routes = [
    {
        path: ':id/delete',
        component: LectureDeletePopupComponent,
        resolve: {
            lecture: LectureResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.lecture.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
