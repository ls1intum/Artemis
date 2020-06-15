import { Routes } from '@angular/router';

import { QuizParticipationComponent } from './quiz-participation.component';
import { QuizExamParticipationComponent } from 'app/exercises/quiz/participate/exam/quiz-exam-participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

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
        component: QuizExamParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
        },
        canActivate: [UserRouteAccessService],
    },
];
