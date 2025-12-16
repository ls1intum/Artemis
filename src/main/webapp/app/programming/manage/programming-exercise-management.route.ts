import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { repositorySubRoutes } from 'app/programming/shared/routes/programming-exercise-repository.route';
import {
    CodeEditorTutorAssessmentContainerComponent,
    canLeaveCodeEditorTutorAssessmentContainer,
} from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';

export const routes: Routes = [
    {
        path: 'programming-exercises/new',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/edit',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import/:exerciseId',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import-from-file',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import-from-sharing',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId',
        loadComponent: () => import('app/programming/manage/detail/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/grading/:tab',
        loadComponent: () =>
            import('app/programming/manage/grading/configure/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/edit-build-plan',
        loadComponent: () => import('app/programming/manage/build-plan-editor/build-plan-editor.component').then((m) => m.BuildPlanEditorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.buildPlanEditor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },
    {
        path: 'programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [canLeaveCodeEditorTutorAssessmentContainer],
    },
];
