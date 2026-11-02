import { quizExerciseUpdateRoute } from 'app/quiz/manage/quiz-exercise-update.route';
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';

import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

export const quizManagementRoute: Routes = [
    quizExerciseUpdateRoute('quiz-exercises/new', undefined, [PendingChangesGuard]),
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
    quizExerciseUpdateRoute('quiz-exercises/:exerciseId/edit', undefined, [PendingChangesGuard]),
    quizExerciseUpdateRoute('quiz-exercises/:exerciseId/import', 'artemisApp.quizExercise.home.importLabel', [PendingChangesGuard]),
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
