import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { LectureService } from './lecture.service';
import { LectureComponent } from './lecture.component';
import { LectureDetailComponent } from './lecture-detail.component';
import { LectureUpdateComponent } from './lecture-update.component';
import { Lecture, LectureAttachmentsComponent } from 'app/entities/lecture';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    constructor(private service: LectureService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<Lecture> {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<Lecture>) => response.ok),
                map((lecture: HttpResponse<Lecture>) => lecture.body!),
            );
        }
        return of(new Lecture());
    }
}

export const lectureRoute: Routes = [
    {
        path: 'course/:courseId/lecture',
        component: LectureComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/lecture/:id/view',
        component: LectureDetailComponent,
        resolve: {
            lecture: LectureResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/lecture/:id/attachments',
        component: LectureAttachmentsComponent,
        resolve: {
            lecture: LectureResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.lecture.attachments.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/lecture/new',
        component: LectureUpdateComponent,
        resolve: {
            lecture: LectureResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/lecture/:id/edit',
        component: LectureUpdateComponent,
        resolve: {
            lecture: LectureResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
