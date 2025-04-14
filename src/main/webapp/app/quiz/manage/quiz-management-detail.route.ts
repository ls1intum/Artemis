import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const quizManagementDetailRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('./detail/quiz-exercise-detail.component').then((m) => m.QuizExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-statistic',
        loadComponent: () => import('app/quiz/manage/statistics/quiz-statistic/quiz-statistic.component').then((m) => m.QuizStatisticComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'quiz-point-statistic',
        loadComponent: () => import('app/quiz/manage/statistics/quiz-point-statistic/quiz-point-statistic.component').then((m) => m.QuizPointStatisticComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'mc-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/multiple-choice-question-statistic/multiple-choice-question-statistic.component').then(
                (m) => m.MultipleChoiceQuestionStatisticComponent,
            ),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'dnd-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component').then((m) => m.DragAndDropQuestionStatisticComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        loadComponent: () =>
            import('app/quiz/manage/statistics/short-answer-question-statistic/short-answer-question-statistic.component').then((m) => m.ShortAnswerQuestionStatisticComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.course.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
