import { Routes } from '@angular/router';

import { QuizParticipationComponent } from './quiz-participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const quizParticipationRoute: Routes = [
    {
        path: ':exerciseId',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'default',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':exerciseId/practice',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
];
