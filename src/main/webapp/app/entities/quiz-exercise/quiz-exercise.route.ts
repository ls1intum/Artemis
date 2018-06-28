import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExercisePopupComponent } from './quiz-exercise-dialog.component';
import { QuizExerciseDeletePopupComponent } from './quiz-exercise-delete-dialog.component';

export const quizExerciseRoute: Routes = [
    {
        path: 'quiz-exercise',
        component: QuizExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'quiz-exercise/:id',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const quizExercisePopupRoute: Routes = [
    {
        path: 'quiz-exercise-new',
        component: QuizExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'quiz-exercise/:id/edit',
        component: QuizExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'quiz-exercise/:id/delete',
        component: QuizExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.quizExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
