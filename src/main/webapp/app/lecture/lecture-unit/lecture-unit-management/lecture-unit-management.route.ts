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
import { CourseResolve } from 'app/course/manage/course-management.route';
import { LectureResolve } from 'app/lecture/lecture.route';

// TODO: The /edit routes have no crumb because individual attachments have no route => make child of overview
export const lectureUnitRoute: Routes = [
    {
        path: ':courseId/lectures/:lectureId/unit-management',
        component: LectureUnitManagementComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
            ],
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/exercise-units/create',
        component: CreateExerciseUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
                { label: 'artemisApp.videoUnit.createExerciseUnit.title', path: 'create' },
            ],
            pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/attachment-units/create',
        component: CreateAttachmentUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
                { label: 'artemisApp.videoUnit.createAttachmentUnit.title', path: 'create' },
            ],
            pageTitle: 'artemisApp.attachmentUnit.createAttachmentUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/attachment-units/:attachmentUnitId/edit',
        component: EditAttachmentUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
            ],
            pageTitle: 'artemisApp.attachmentUnit.editAttachmentUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/video-units/create',
        component: CreateVideoUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
                { label: 'artemisApp.videoUnit.createVideoUnit.title', path: 'create' },
            ],
            pageTitle: 'artemisApp.videoUnit.createVideoUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/video-units/:videoUnitId/edit',
        component: EditVideoUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
            ],
            pageTitle: 'artemisApp.videoUnit.editVideoUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/text-units/create',
        component: CreateTextUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
                { label: 'artemisApp.videoUnit.createVideoUnit.title', path: 'create' },
            ],
            pageTitle: 'artemisApp.textUnit.createTextUnit.title',
        },
    },
    {
        path: ':courseId/lectures/:lectureId/unit-management/text-units/:textUnitId/edit',
        component: EditTextUnitComponent,
        resolve: {
            course: CourseResolve,
            lecture: LectureResolve,
        },
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.lecture.home.title', path: 'lectures' },
                { variable: 'lecture.title', path: 'lecture.id' },
                { label: 'artemisApp.lectureUnit.home.title', path: 'unit-management' },
            ],
            pageTitle: 'artemisApp.textUnit.editTextUnit.title',
        },
    },
];
