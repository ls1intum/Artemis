import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { CreateExerciseUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-exercise-unit/create-exercise-unit.component';
import { CreateAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/create-attachment-unit/create-attachment-unit.component';
import { EditAttachmentUnitComponent } from 'app/lecture/lecture-unit/lecture-unit-management/edit-attachment-unit/edit-attachment-unit.component';

export const lectureUnitRoute: Routes = [
    {
        path: ':courseId/lectures/:lectureId/unit-management',
        component: LectureUnitManagementComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/exercise-units/create',
        component: CreateExerciseUnitComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/attachment-units/create',
        component: CreateAttachmentUnitComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/attachment-units/:attachmentUnitId/edit',
        component: EditAttachmentUnitComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.attachmentUnit.editAttachmentUnit.title',
        },
    },
];
