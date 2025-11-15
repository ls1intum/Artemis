import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_EDITOR } from 'app/shared/constants/authority.constants';
import { lectureUnitRoute } from 'app/lecture/manage/lecture-units/lecture-unit-management.route';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';
import { hasLectureUnsavedChangesGuard } from './hasLectureUnsavedChanges.guard';
import { AttachmentResolve, LectureResolve } from 'app/lecture/manage/services/lecture-resolve.service';

export const lectureRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('./lecture/lecture.component').then((m) => m.LectureComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
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
                loadComponent: () => import('./lecture-update/lecture-update.component').then((m) => m.LectureUpdateComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'global.generic.create',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':lectureId',
                loadComponent: () => import('./lecture-detail/lecture-detail.component').then((m) => m.LectureDetailComponent),
                resolve: {
                    lecture: LectureResolve,
                },
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
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
                        loadComponent: () => import('app/lecture/manage/lecture-attachments/lecture-attachments.component').then((m) => m.LectureAttachmentsComponent),
                        data: {
                            authorities: IS_AT_LEAST_EDITOR,
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
                                },
                            },
                        ],
                    },
                    {
                        path: 'edit',
                        loadComponent: () => import('./lecture-update/lecture-update.component').then((m) => m.LectureUpdateComponent),
                        data: {
                            authorities: IS_AT_LEAST_EDITOR,
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
