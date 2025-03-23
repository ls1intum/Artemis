import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';
import { lectureUnitRoute } from 'app/lecture/manage/lecture-units/lecture-unit-management.route';
import { CourseManagementResolve } from 'app/core/course/manage/course-management-resolve.service';
import { hasLectureUnsavedChangesGuard } from './hasLectureUnsavedChanges.guard';
import { AttachmentResolve, LectureResolve } from 'app/lecture/manage/lecture-resolve.service';

export const lectureRoute: Routes = [
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
                        loadComponent: () => import('app/lecture/manage/lecture-attachments.component').then((m) => m.LectureAttachmentsComponent),
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
                                loadComponent: () => import('app/lecture/manage/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
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
];
