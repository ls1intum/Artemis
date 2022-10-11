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
import { LectureUpdateWizardComponent } from './lecture-update-wizard.component';
import { Lecture } from 'app/entities/lecture.model';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { lectureUnitRoute } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.route';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    constructor(private lectureService: LectureService) {}

    resolve(route: ActivatedRouteSnapshot): Observable<Lecture> {
        const lectureId = route.params['lectureId'];
        if (lectureId) {
            return this.lectureService.find(lectureId).pipe(
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
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lecture.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // Create a new path without a component defined to prevent the LectureComponent from being always rendered
        path: ':courseId/lectures',
        resolve: {
            course: CourseManagementResolve,
        },
        children: [
            {
                path: 'new',
                component: LectureUpdateComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'global.generic.create',
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
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.lecture.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':lectureId',
                resolve: {
                    lecture: LectureResolve,
                },
                children: [
                    {
                        path: 'attachments',
                        component: LectureAttachmentsComponent,
                        data: {
                            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'artemisApp.lecture.attachments.title',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: 'edit',
                        component: LectureUpdateComponent,
                        data: {
                            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'global.generic.edit',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    ...lectureUnitRoute,
                ],
            },
        ],
    },
];
