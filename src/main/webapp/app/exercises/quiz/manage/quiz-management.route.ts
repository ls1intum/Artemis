import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseExportComponent } from './quiz-exercise-export.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { QuizParticipationComponent } from 'app/exercises/quiz/participate/quiz-participation.component';

export const quizManagementRoute: Routes = [
    {
        path: ':courseId/quiz-exercises',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/re-evaluate',
        component: QuizReEvaluateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/edit',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/export',
        component: QuizExerciseExportComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/preview',
        component: QuizParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/solution',
        component: QuizParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
];
