import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseExportComponent } from './quiz-exercise-export.component';
import { QuizExerciseDeletePopupComponent } from './quiz-exercise-delete-dialog.component';
import { QuizReEvaluateComponent } from '../../quiz/re-evaluate/quiz-re-evaluate.component';

export const quizExerciseRoute: Routes = [
    {
        path: 'quiz-exercise',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz-exercise/:id',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/re-evaluate/:id',
        component: QuizReEvaluateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/edit/:id',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/quiz-exercise/export',
        component: QuizExerciseExportComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizExercisePopupRoute: Routes = [
    {
        path: 'quiz-exercise/:id/delete',
        component: QuizExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
