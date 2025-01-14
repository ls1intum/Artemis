import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const quizStatisticRoute: Routes = [
    {
        path: ':courseId/quiz-exercises/:exerciseId/quiz-statistic',
        loadComponent: () => import('./quiz-statistic/quiz-statistic.component').then((m) => m.QuizStatisticComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/quiz-point-statistic',
        loadComponent: () => import('./quiz-point-statistic/quiz-point-statistic.component').then((m) => m.QuizPointStatisticComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/mc-question-statistic/:questionId',
        loadComponent: () => import('./multiple-choice-question-statistic/multiple-choice-question-statistic.component').then((m) => m.MultipleChoiceQuestionStatisticComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/dnd-question-statistic/:questionId',
        loadComponent: () => import('./drag-and-drop-question-statistic/drag-and-drop-question-statistic.component').then((m) => m.DragAndDropQuestionStatisticComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        loadComponent: () => import('./short-answer-question-statistic/short-answer-question-statistic.component').then((m) => m.ShortAnswerQuestionStatisticComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
