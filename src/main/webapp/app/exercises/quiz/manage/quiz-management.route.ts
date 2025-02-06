import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

import { Authority } from 'app/shared/constants/authority.constants';

export const quizManagementRoute: Routes = [
    {
        path: 'quiz-exercises/new',
        loadComponent: () => import('./quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/export',
        loadComponent: () => import('./quiz-exercise-export.component').then((m) => m.QuizExerciseExportComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/quiz/manage/quiz-exercise-detail.component').then((m) => m.QuizExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId/re-evaluate',
        loadComponent: () => import('app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component').then((m) => m.QuizReEvaluateComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId/edit',
        loadComponent: () => import('./quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/:exerciseId/import',
        loadComponent: () => import('./quiz-exercise-update.component').then((m) => m.QuizExerciseUpdateComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'quiz-exercises/:exerciseId/preview',
        loadComponent: () => import('app/exercises/quiz/participate/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-exercises/:exerciseId/solution',
        loadComponent: () => import('app/exercises/quiz/participate/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
];
