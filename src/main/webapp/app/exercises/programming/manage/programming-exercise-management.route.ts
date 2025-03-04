import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-resolve.service';
import { repositorySubRoutes } from 'app/exercises/programming/shared/routes/programming-exercise-repository.route';
import { isOrion } from 'app/shared/orion/orion';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';

export const routes: Routes = [
    {
        path: 'programming-exercises/new',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/edit',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import/:exerciseId',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import-from-file',
        loadComponent: () => import('app/exercises/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId',
        loadComponent: () => import('app/exercises/programming/manage/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/grading/:tab',
        loadComponent: () =>
            import('app/exercises/programming/manage/grading/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercises/shared/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/iris-settings',
        loadChildren: () => import('app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update-route').then((m) => m.routes),
    },
    {
        path: 'programming-exercises/:exerciseId/edit-build-plan',
        loadComponent: () => import('app/exercises/programming/manage/build-plan-editor.component').then((m) => m.BuildPlanEditorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
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
        component: !isOrion ? CodeEditorTutorAssessmentContainerComponent : OrionTutorAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
