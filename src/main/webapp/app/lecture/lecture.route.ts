import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { LectureService } from './lecture.service';
import { LectureComponent } from './lecture.component';
import { LectureDetailComponent } from './lecture-detail.component';
import { LectureUpdateComponent } from './lecture-update.component';
import { Lecture } from 'app/entities/lecture.model';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseResolve } from 'app/course/manage/course-management.route';
import { lectureUnitRoute } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.route';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    constructor(private service: LectureService) {}

    resolve(route: ActivatedRouteSnapshot): Observable<Lecture> {
        const id = route.params['lectureId'];
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
        path: ':courseId/lectures',
        component: LectureComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
            ],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // Create a new path without a component defined to prevent the LectureComponent from being always rendered
        path: ':courseId/lectures',
        resolve: {
            course: CourseResolve,
        },
        data: {
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
            ],
        },
        children: [
            {
                path: 'new',
                component: LectureUpdateComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'global.generic.create',
                    breadcrumbs: [],
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':lectureId',
                component: LectureDetailComponent,
                resolve: {
                    lecture: LectureResolve,
                },
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    breadcrumbLabelVariable: 'lecture.title',
                    pageTitle: 'artemisApp.lecture.home.title',
                    breadcrumbs: [],
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':lectureId',
                resolve: {
                    lecture: LectureResolve,
                },
                data: {
                    breadcrumbLabelVariable: 'lecture.title',
                    breadcrumbs: [],
                },
                children: [
                    {
                        path: 'attachments',
                        component: LectureAttachmentsComponent,
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.lecture.attachments.title',
                            breadcrumbLabelVariable: '',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'edit',
                        component: LectureUpdateComponent,
                        data: {
                            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'global.generic.edit',
                            breadcrumbLabelVariable: '',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    ...lectureUnitRoute,
                ],
            },
        ],
    },
];
