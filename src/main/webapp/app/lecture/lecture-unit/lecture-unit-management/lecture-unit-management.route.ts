import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
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
import { AttachmentUnitsComponent } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component';
import { PdfPreviewComponent } from 'app/lecture/pdf-preview/pdf-preview.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

@Injectable({ providedIn: 'root' })
export class AttachmentUnitResolve implements Resolve<AttachmentUnit> {
    private attachmentUnitService = inject(AttachmentUnitService);

    resolve(route: ActivatedRouteSnapshot): Observable<AttachmentUnit> {
        const lectureId = route.params['lectureId'];
        const attachmentUnitId = route.params['attachmentUnitId'];
        if (attachmentUnitId) {
            return this.attachmentUnitService.findById(attachmentUnitId, lectureId).pipe(
                filter((response: HttpResponse<AttachmentUnit>) => response.ok),
                map((attachmentUnit: HttpResponse<AttachmentUnit>) => attachmentUnit.body!),
            );
        }
        return of(new AttachmentUnit());
    }
}

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
                path: 'attachment-units/process',
                component: AttachmentUnitsComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnits.pageTitle',
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
                path: 'attachment-units/:attachmentUnitId/view',
                component: PdfPreviewComponent,
                resolve: {
                    course: CourseManagementResolve,
                    attachmentUnit: AttachmentUnitResolve,
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
