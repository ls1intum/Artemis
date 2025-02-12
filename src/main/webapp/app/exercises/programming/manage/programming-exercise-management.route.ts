import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-resolve.service';

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
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/repo/:repositoryId',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/repo/:repositoryId/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/repo/:repositoryId/vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/participations/:participationId/repository',
        loadComponent: () => import('app/localvc/repository-view/repository-view.component').then((m) => m.RepositoryViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/participations/:participationId/repository/commit-history',
        loadComponent: () => import('app/localvc/commit-history/commit-history.component').then((m) => m.CommitHistoryComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/participations/:participationId/repository/vcs-access-log',
        loadComponent: () => import('app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component').then((m) => m.VcsRepositoryAccessLogViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/participations/:participationId/repository/commit-history/:commitHash',
        loadComponent: () => import('app/localvc/commit-details-view/commit-details-view.component').then((m) => m.CommitDetailsViewComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'programming-exercises/:exerciseId/submissions/:submissionId',
        loadChildren: () => import('app/exercises/programming/assess/programming-assessment.route').then((m) => m.routes),
    },
];
