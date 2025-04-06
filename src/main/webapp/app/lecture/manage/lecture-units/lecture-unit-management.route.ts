import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';
import { AttachmentUnitResolve } from 'app/lecture/manage/lecture-units/lecture-unit-management-resolve.service';

export const lectureUnitRoute: Routes = [
    {
        path: 'unit-management',
        loadComponent: () => import('app/lecture/manage/lecture-units/lecture-unit-management.component').then((m) => m.LectureUnitManagementComponent),
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
                loadComponent: () => import('app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component').then((m) => m.CreateExerciseUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
                },
            },
            {
                path: 'attachment-units/process',
                loadComponent: () => import('app/lecture/manage/lecture-units/attachment-units/attachment-units.component').then((m) => m.AttachmentUnitsComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnits.pageTitle',
                },
            },
            {
                path: 'attachment-units/create',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/create-attachment-unit/create-attachment-unit.component').then((m) => m.CreateAttachmentUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnit.title',
                },
            },
            {
                path: 'video-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-video-unit/create-video-unit.component').then((m) => m.CreateVideoUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.createVideoUnit.title',
                },
            },
            {
                path: 'online-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-online-unit/create-online-unit.component').then((m) => m.CreateOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.createOnlineUnit.title',
                },
            },
            {
                path: 'text-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-text-unit/create-text-unit.component').then((m) => m.CreateTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.createTextUnit.title',
                },
            },
            {
                path: 'attachment-units/:attachmentUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-attachment-unit/edit-attachment-unit.component').then((m) => m.EditAttachmentUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.editAttachmentUnit.title',
                },
            },
            {
                path: 'attachment-units/:attachmentUnitId/view',
                loadComponent: () => import('app/lecture/manage/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
                resolve: {
                    course: CourseManagementResolve,
                    attachmentUnit: AttachmentUnitResolve,
                },
            },
            {
                path: 'video-units/:videoUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-video-unit/edit-video-unit.component').then((m) => m.EditVideoUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.editVideoUnit.title',
                },
            },
            {
                path: 'online-units/:onlineUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-online-unit/edit-online-unit.component').then((m) => m.EditOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.editOnlineUnit.title',
                },
            },
            {
                path: 'text-units/:textUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-text-unit/edit-text-unit.component').then((m) => m.EditTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.editTextUnit.title',
                },
            },
        ],
    },
];
