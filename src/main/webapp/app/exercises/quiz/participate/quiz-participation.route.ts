import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const routes: Routes = [
    {
        path: 'live',
        loadComponent: () => import('./quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'live',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'practice',
        loadComponent: () => import('./quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
];
