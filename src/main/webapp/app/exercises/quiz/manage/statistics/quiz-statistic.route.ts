import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { QuizStatisticComponent } from './quiz-statistic/quiz-statistic.component';
import { QuizPointStatisticComponent } from './quiz-point-statistic/quiz-point-statistic.component';
import { MultipleChoiceQuestionStatisticComponent } from './multiple-choice-question-statistic/multiple-choice-question-statistic.component';
import { DragAndDropQuestionStatisticComponent } from './drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';
import { ShortAnswerQuestionStatisticComponent } from './short-answer-question-statistic/short-answer-question-statistic.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';

export const quizStatisticRoute: Routes = [
    {
        path: ':courseId/quiz-exercises/:exerciseId/quiz-statistic',
        component: QuizStatisticComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/quiz-point-statistic',
        component: QuizPointStatisticComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/mc-question-statistic/:questionId',
        component: MultipleChoiceQuestionStatisticComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/dnd-question-statistic/:questionId',
        component: DragAndDropQuestionStatisticComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/sa-question-statistic/:questionId',
        component: ShortAnswerQuestionStatisticComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.course.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
];
