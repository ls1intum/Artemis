import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Injectable, NgModule, inject } from '@angular/core';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { CodeHintGenerationOverviewComponent } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/code-hint-generation-overview.component';
import { BuildPlanEditorComponent } from 'app/exercises/programming/manage/build-plan-editor.component';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
import { VcsRepositoryAccessLogViewComponent } from 'app/localvc/vcs-repository-access-log-view/vcs-repository-access-log-view.component';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
    private service = inject(ProgrammingExerciseService);

    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = route.params['exerciseId'] ? route.params['exerciseId'] : undefined;
        if (exerciseId) {
            return this.service.find(exerciseId, true).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body!));
        }
        return of(new ProgrammingExercise(undefined, undefined));
    }
}

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises/new',
        component: ProgrammingExerciseUpdateComponent,
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
        path: ':courseId/programming-exercises/:exerciseId/edit',
        component: ProgrammingExerciseUpdateComponent,
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
        path: ':courseId/programming-exercises/import/:exerciseId',
        component: ProgrammingExerciseUpdateComponent,
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
        path: ':courseId/programming-exercises/import-from-file',
        component: ProgrammingExerciseUpdateComponent,
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
        path: ':courseId/programming-exercises/:exerciseId',
        component: ProgrammingExerciseDetailComponent,
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
        path: ':courseId/programming-exercises/:exerciseId/plagiarism',
        component: PlagiarismInspectorComponent,
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
        path: ':courseId/programming-exercises/:exerciseId/grading/:tab',
        component: ProgrammingExerciseConfigureGradingComponent,
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/exercise-statistics',
        component: ExerciseStatisticsComponent,
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
        path: ':courseId/programming-exercises/:exerciseId/exercise-hints/code-hint-management',
        component: CodeHintGenerationOverviewComponent,
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.codeHint.management.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/iris-settings',
        loadChildren: () =>
            import('app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update-routing.module').then((m) => m.IrisExerciseSettingsUpdateRoutingModule),
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/edit-build-plan',
        component: BuildPlanEditorComponent,
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
        path: ':courseId/programming-exercises/:exerciseId/repository/:repositoryType',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/repository/:repositoryType/commit-history',
        component: CommitHistoryComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/repository/:repositoryType/vcs-access-log',
        component: VcsRepositoryAccessLogViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/repository/:repositoryType/commit-history/:commitHash',
        component: CommitDetailsViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/participations/:participationId/repository',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/participations/:participationId/repository/commit-history',
        component: CommitHistoryComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/participations/:participationId/repository/vcs-access-log',
        component: VcsRepositoryAccessLogViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/participations/:participationId/repository/commit-history/:commitHash',
        component: CommitDetailsViewComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingExerciseManagementRoutingModule {}
