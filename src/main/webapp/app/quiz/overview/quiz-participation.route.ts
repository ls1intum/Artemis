import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const routes: Routes = [
    {
        path: 'live',
        loadComponent: () => import('./participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'overview.exercises',
            mode: 'live',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'practice',
        loadComponent: () => import('./participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'overview.exercises',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'practice/:participationId',
        loadComponent: () => import('./participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'overview.exercises',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'practice/:participationId/submission/:submissionId',
        loadComponent: () => import('./participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
        data: {
            authorities: [],
            pageTitle: 'overview.exercises',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
];
