import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { CreateExerciseUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-exercise-unit/create-exercise-unit.component';
import { CreateAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-attachment-unit/create-attachment-unit.component';
import { EditAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component';
import { CreateTextUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-text-unit/create-text-unit.component';
import { EditTextUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-text-unit/edit-text-unit.component';
import { CreateVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-video-unit/create-video-unit.component';
import { EditVideoUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-video-unit/edit-video-unit.component';
import { CreateOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-online-unit/create-online-unit.component';
import { EditOnlineUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-online-unit/edit-online-unit.component';

export const lectureUnitRoute: Routes = [
    {
        path: 'unit-management',
        component: LectureUnitManagementComponent,
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
                component: CreateExerciseUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
                },
            },
            {
                path: 'attachment-units/create',
                component: CreateAttachmentUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnit.title',
                },
            },
            {
                path: 'video-units/create',
                component: CreateVideoUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.createVideoUnit.title',
                },
            },
            {
                path: 'online-units/create',
                component: CreateOnlineUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.createOnlineUnit.title',
                },
            },
            {
                path: 'text-units/create',
                component: CreateTextUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.createTextUnit.title',
                },
            },
            {
                path: 'attachment-units/:attachmentUnitId/edit',
                component: EditAttachmentUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.editAttachmentUnit.title',
                },
            },
            {
                path: 'video-units/:videoUnitId/edit',
                component: EditVideoUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.videoUnit.editVideoUnit.title',
                },
            },
            {
                path: 'online-units/:onlineUnitId/edit',
                component: EditOnlineUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.editOnlineUnit.title',
                },
            },
            {
                path: 'text-units/:textUnitId/edit',
                component: EditTextUnitComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.editTextUnit.title',
                },
            },
        ],
    },
];
