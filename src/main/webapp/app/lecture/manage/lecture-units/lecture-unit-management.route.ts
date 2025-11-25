import { Routes } from '@angular/router';
import { IS_AT_LEAST_EDITOR } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AttachmentVideoUnitResolve } from 'app/lecture/manage/lecture-units/services/lecture-unit-management-resolve.service';

export const lectureUnitRoute: Routes = [
    {
        path: 'unit-management',
        loadComponent: () => import('app/lecture/manage/lecture-units/management/lecture-unit-management.component').then((m) => m.LectureUnitManagementComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
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
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
                },
            },
            {
                path: 'attachment-video-units/process',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component').then((m) => m.AttachmentVideoUnitsComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.pageTitle',
                },
            },
            {
                path: 'attachment-video-units/create',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/create-attachment-video-unit/create-attachment-video-unit.component').then(
                        (m) => m.CreateAttachmentVideoUnitComponent,
                    ),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.attachmentVideoUnit.createAttachmentVideoUnit.title',
                },
            },
            {
                path: 'online-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-online-unit/create-online-unit.component').then((m) => m.CreateOnlineUnitComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.onlineUnit.createOnlineUnit.title',
                },
            },
            {
                path: 'text-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-text-unit/create-text-unit.component').then((m) => m.CreateTextUnitComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.textUnit.createTextUnit.title',
                },
            },
            {
                path: 'attachment-video-units/:attachmentVideoUnitId/edit',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/edit-attachment-video-unit/edit-attachment-video-unit.component').then((m) => m.EditAttachmentVideoUnitComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.attachmentVideoUnit.editAttachmentVideoUnit.title',
                },
            },
            {
                path: 'attachment-video-units/:attachmentVideoUnitId/view',
                loadComponent: () => import('app/lecture/manage/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
                resolve: {
                    attachmentVideoUnit: AttachmentVideoUnitResolve,
                },
            },
            {
                path: 'online-units/:onlineUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-online-unit/edit-online-unit.component').then((m) => m.EditOnlineUnitComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.onlineUnit.editOnlineUnit.title',
                },
            },
            {
                path: 'text-units/:textUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-text-unit/edit-text-unit.component').then((m) => m.EditTextUnitComponent),
                data: {
                    authorities: IS_AT_LEAST_EDITOR,
                    pageTitle: 'artemisApp.textUnit.editTextUnit.title',
                },
            },
        ],
    },
];
