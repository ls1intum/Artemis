import { Routes } from '@angular/router';

import { QuizParticipationComponent } from './quiz-participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';

export const quizParticipationRoute: Routes = [
    {
        path: 'live',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'live',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: 'practice',
        component: QuizParticipationComponent,
        data: {
            authorities: [],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'practice',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
];
