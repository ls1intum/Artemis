import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { QuizParticipationComponent } from './quiz-participation.component';

export const quizParticipationRoute: Routes = [
    {
        path: 'live',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'live',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'practice',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
];
