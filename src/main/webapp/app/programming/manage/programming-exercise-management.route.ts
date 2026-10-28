import {
    programmingExerciseBuildPlanRoute,
    programmingExerciseDetailRoute,
    programmingExerciseGradingRoute,
    programmingExerciseUpdateRoute,
    programmingExerciseVersionHistoryRoute,
} from 'app/programming/manage/programming-exercise-shared.route';
import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

import { MetisGuard } from 'app/metis/shared/metis-guard.service';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { repositorySubRoutes } from 'app/programming/shared/routes/programming-exercise-repository.route';
import {
    CodeEditorTutorAssessmentContainerComponent,
    canLeaveCodeEditorTutorAssessmentContainer,
} from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';

export const routes: Routes = [
    programmingExerciseUpdateRoute('programming-exercises/new'),
    programmingExerciseUpdateRoute('programming-exercises/:exerciseId/edit'),
    programmingExerciseUpdateRoute('programming-exercises/import/:exerciseId', 'artemisApp.programmingExercise.home.importLabel'),
    programmingExerciseUpdateRoute('programming-exercises/import-from-file', 'artemisApp.programmingExercise.home.importLabel'),
    programmingExerciseUpdateRoute('programming-exercises/import-from-sharing', 'artemisApp.programmingExercise.home.importLabel'),
    programmingExerciseDetailRoute('programming-exercises/:exerciseId'),
    programmingExerciseVersionHistoryRoute('programming-exercises/:exerciseId/version-history'),
    {
        path: 'programming-exercises/:exerciseId/generation/runs/:runId',
        canActivate: [
            ({ params }) =>
                inject(Router).createUrlTree(['/course-management', params['courseId'], 'programming-exercises', params['exerciseId']], {
                    queryParams: { aiRun: `authoring:${params['exerciseId']}:${params['runId']}` },
                }),
        ],
        children: [],
    },
    {
        path: 'programming-exercises/:exerciseId/generation',
        canActivate: [
            ({ params, queryParams }) =>
                inject(Router).createUrlTree(['/course-management', params['courseId'], 'programming-exercises', params['exerciseId']], {
                    queryParams: { aiRun: `authoring:${params['exerciseId']}:${queryParams['run'] ?? 'latest'}` },
                }),
        ],
        children: [],
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
    programmingExerciseGradingRoute('programming-exercises/:exerciseId/grading/:tab'),
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
        path: 'programming-exercises/:exerciseId/comprehension-check',
        loadComponent: () => import('app/metis/manage/dashboard/metis-dashboard.component').then((m) => m.MetisDashboardComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.metis.dashboard.title',
        },
        // the comprehension check is a module of its own, so a direct URL must not load it when the module is off
        canActivate: [UserRouteAccessService, MetisGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/comprehension-check/review',
        loadComponent: () => import('app/metis/manage/question-review/metis-question-review.component').then((m) => m.MetisQuestionReviewComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.metis.review.title',
        },
        canActivate: [UserRouteAccessService, MetisGuard],
    },
    programmingExerciseBuildPlanRoute('programming-exercises/:exerciseId/edit-build-plan'),
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
