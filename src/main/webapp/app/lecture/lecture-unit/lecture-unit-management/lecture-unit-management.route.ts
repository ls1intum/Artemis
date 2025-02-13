import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { AttachmentUnitResolve } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management-resolve.service';

export const lectureUnitRoute: Routes = [
    {
        path: 'unit-management',
        loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component').then((m) => m.LectureUnitManagementComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // Create a new path without a component defined to prevent the LectureUnitManagementComponent from being always rendered
        path: 'unit-management',
        data: {
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        children: [
            {
                path: 'exercise-units/create',
                loadComponent: () =>
                    import('app/lecture/lecture-unit/lecture-unit-management/create-exercise-unit/create-exercise-unit.component').then((m) => m.CreateExerciseUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
                },
            },
            {
                path: 'attachment-units/process',
                loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component').then((m) => m.AttachmentUnitsComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnits.pageTitle',
                },
            },
            {
                path: 'attachment-units/create',
                loadComponent: () =>
                    import('app/lecture/lecture-unit/lecture-unit-management/create-attachment-unit/create-attachment-unit.component').then((m) => m.CreateAttachmentUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnit.title',
                },
            },
            {
                path: 'video-units/create',
                loadComponent: () =>
                    import('app/lecture/lecture-unit/lecture-unit-management/create-video-unit/create-video-unit.component').then((m) => m.CreateVideoUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.createVideoUnit.title',
                },
            },
            {
                path: 'online-units/create',
                loadComponent: () =>
                    import('app/lecture/lecture-unit/lecture-unit-management/create-online-unit/create-online-unit.component').then((m) => m.CreateOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.createOnlineUnit.title',
                },
            },
            {
                path: 'text-units/create',
                loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/create-text-unit/create-text-unit.component').then((m) => m.CreateTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.createTextUnit.title',
                },
            },
            {
                path: 'attachment-units/:attachmentUnitId/edit',
                loadComponent: () =>
                    import('app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component').then((m) => m.EditAttachmentUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.editAttachmentUnit.title',
                },
            },
            {
                path: 'attachment-units/:attachmentUnitId/view',
                loadComponent: () => import('app/lecture/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
                resolve: {
                    course: CourseManagementResolve,
                    attachmentUnit: AttachmentUnitResolve,
                },
            },
            {
                path: 'video-units/:videoUnitId/edit',
                loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/edit-video-unit/edit-video-unit.component').then((m) => m.EditVideoUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.editVideoUnit.title',
                },
            },
            {
                path: 'online-units/:onlineUnitId/edit',
                loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component').then((m) => m.EditOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.editOnlineUnit.title',
                },
            },
            {
                path: 'text-units/:textUnitId/edit',
                loadComponent: () => import('app/lecture/lecture-unit/lecture-unit-management/edit-text-unit/edit-text-unit.component').then((m) => m.EditTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.editTextUnit.title',
                },
            },
        ],
    },
];
