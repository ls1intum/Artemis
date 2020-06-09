import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupDetailComponent } from 'app/exam/manage/exercise-groups/exercise-group-detail.component';

export const examManagementRoute: Routes = [
    {
        path: '',
        component: ExamManagementComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: ExamUpdateComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/edit',
        component: ExamUpdateComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/view',
        component: ExamDetailComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups',
        component: ExerciseGroupsComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/new',
        component: ExerciseGroupUpdateComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/:exerciseGroupId/edit',
        component: ExerciseGroupUpdateComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/exerciseGroups/:exerciseGroupId/view',
        component: ExerciseGroupDetailComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/students',
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/studentExams',
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':examId/studentExams/:studentExamId/view',
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.examManagement.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_MANAGEMENT_ROUTES = [...examManagementRoute];

export const examManagementState: Routes = [
    {
        path: '',
        children: EXAM_MANAGEMENT_ROUTES,
    },
];
