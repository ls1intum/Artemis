import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { QuizComponent } from './quiz.component';

export const quizRoute: Routes = [
    {
        path: 'quiz/:id',
        component: QuizComponent,
        data: {
            authorities: [],
            pageTitle: 'arTeMiSApp.quizExercise.home.title',
            mode: 'default'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz/:id/practice',
        component: QuizComponent,
        data: {
            authorities: [],
            pageTitle: 'arTeMiSApp.quizExercise.home.title',
            mode: 'practice'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz/:id/preview',
        component: QuizComponent,
        data: {
            authorities: [],
            pageTitle: 'arTeMiSApp.quizExercise.home.title',
            mode: 'preview'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'quiz/:id/solution',
        component: QuizComponent,
        data: {
            authorities: [],
            pageTitle: 'arTeMiSApp.quizExercise.home.title',
            mode: 'solution'
        },
        canActivate: [UserRouteAccessService]
    }
];
