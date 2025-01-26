import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { LectureService } from './lecture.service';

import { Lecture } from 'app/entities/lecture.model';

import { Authority } from 'app/shared/constants/authority.constants';
import { lectureUnitRoute } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.route';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

import { Attachment } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { hasLectureUnsavedChangesGuard } from './hasLectureUnsavedChanges.guard';

@Injectable({ providedIn: 'root' })
export class LectureResolve implements Resolve<Lecture> {
    private lectureService = inject(LectureService);

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

@Injectable({ providedIn: 'root' })
export class AttachmentResolve implements Resolve<Attachment> {
    private attachmentService = inject(AttachmentService);

    resolve(route: ActivatedRouteSnapshot): Observable<Attachment> {
        const attachmentId = route.params['attachmentId'];
        if (attachmentId) {
            return this.attachmentService.find(attachmentId).pipe(
                filter((response: HttpResponse<Attachment>) => response.ok),
                map((attachment: HttpResponse<Attachment>) => attachment.body!),
            );
        }
        return of(new Attachment());
    }
}

export const lectureRoute: Routes = [
    {
        path: ':courseId/lectures',
        loadComponent: () => import('app/course/manage/course-management-tab-bar/course-management-tab-bar.component').then((m) => m.CourseManagementTabBarComponent),
        children: [
            {
                path: '',
                loadComponent: () => import('./lecture.component').then((m) => m.LectureComponent),
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
                path: '',
                resolve: {
                    course: CourseManagementResolve,
                },
                children: [
                    {
                        path: 'new',
                        loadComponent: () => import('./lecture-update.component').then((m) => m.LectureUpdateComponent),
                        data: {
                            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                            pageTitle: 'global.generic.create',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                    {
                        path: ':lectureId',
                        loadComponent: () => import('./lecture-detail.component').then((m) => m.LectureDetailComponent),
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
                                loadComponent: () => import('app/lecture/lecture-attachments.component').then((m) => m.LectureAttachmentsComponent),
                                data: {
                                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'artemisApp.lecture.attachments.title',
                                },
                                canActivate: [UserRouteAccessService],
                            },
                            {
                                path: 'attachments',
                                canActivate: [UserRouteAccessService],
                                children: [
                                    {
                                        path: ':attachmentId',
                                        loadComponent: () => import('app/lecture/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
                                        resolve: {
                                            attachment: AttachmentResolve,
                                            course: CourseManagementResolve,
                                        },
                                    },
                                ],
                            },
                            {
                                path: 'edit',
                                loadComponent: () => import('./lecture-update.component').then((m) => m.LectureUpdateComponent),
                                data: {
                                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                                    pageTitle: 'global.generic.edit',
                                },
                                canActivate: [UserRouteAccessService],
                                canDeactivate: [hasLectureUnsavedChangesGuard],
                            },
                            ...lectureUnitRoute,
                        ],
                    },
                ],
            },
        ],
    },
];
