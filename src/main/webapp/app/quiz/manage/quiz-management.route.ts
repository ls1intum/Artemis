import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

export const quizManagementRoute: Routes = [
    {
        path: 'quiz-exercises/new',
        loadComponent: () => import('./update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/export',
        loadComponent: () => import('./export/quiz-exercise-export.component').then((m) => m.QuizExerciseExportComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId',
        loadChildren: () => import('./quiz-management-detail.route').then((m) => m.quizManagementDetailRoute),
    },
    {
        path: 'quiz-exercises/:exerciseId/re-evaluate',
        loadComponent: () => import('app/quiz/manage/re-evaluate/quiz-re-evaluate.component').then((m) => m.QuizReEvaluateComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId/edit',
        loadComponent: () => import('./update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/:exerciseId/import',
        loadComponent: () => import('./update/quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.quizExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/:exerciseId/preview',
        loadComponent: () => import('app/quiz/overview/participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId/solution',
        loadComponent: () => import('app/quiz/overview/participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
];
