import { Routes } from '@angular/router';

import { QuizParticipationComponent } from './quiz-participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const quizParticipationRoute: Routes = [
    {
        path: 'courses/:courseId/quiz-exercises/:exerciseId',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'default',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/:courseId/quiz-exercises/:exerciseId/practice',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/:exerciseId/preview',
        component: QuizParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/quiz-exercises/:exerciseId/solution',
        component: QuizParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
        },
        canActivate: [UserRouteAccessService],
    },
];
