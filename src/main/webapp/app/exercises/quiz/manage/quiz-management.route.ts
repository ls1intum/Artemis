import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { QuizExerciseDetailComponent } from './quiz-exercise-detail.component';
import { QuizExerciseExportComponent } from './quiz-exercise-export.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { QuizReEvaluateComponent } from 'app/exercises/quiz/manage/re-evaluate/quiz-re-evaluate.component';
import { QuizParticipationComponent } from 'app/exercises/quiz/participate/quiz-participation.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';

export const quizManagementRoute: Routes = [
    {
        path: ':courseId/quiz-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/quiz-exercises/new',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/export',
        component: QuizExerciseExportComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/re-evaluate',
        component: QuizReEvaluateComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/edit',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/import',
        component: QuizExerciseDetailComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.importLabel',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/preview',
        component: QuizParticipationComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'preview',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
    {
        path: ':courseId/quiz-exercises/:exerciseId/solution',
        component: QuizParticipationComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.quizExercise.home.title',
            mode: 'solution',
            profile: ProfileToggle.QUIZ,
        },
        canActivate: [UserRouteAccessService, ProfileToggleGuard],
    },
];
