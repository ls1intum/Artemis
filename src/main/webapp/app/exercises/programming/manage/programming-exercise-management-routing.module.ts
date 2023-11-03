import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Injectable, NgModule } from '@angular/core';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
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
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
    constructor(private service: ProgrammingExerciseService) {}

    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = route.params['exerciseId'];
        if (exerciseId) {
            return this.service.find(exerciseId, true).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body!));
        }
        return of(new ProgrammingExercise(undefined, undefined));
    }
}

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/programming-exercises',
        component: CourseManagementTabBarComponent,
        children: [
            {
                path: 'new',
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
                path: 'import/:exerciseId',
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
                path: 'import-from-file',
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
                path: ':exerciseId/edit',
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
                path: ':exerciseId',
                component: ProgrammingExerciseDetailComponent,
                resolve: {
                    programmingExercise: ProgrammingExerciseResolve,
                },
                data: {
                    authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.programmingExercise.home.title',
                },
                canActivate: [UserRouteAccessService],
                pathMatch: 'full',
            },
            {
                path: ':exerciseId/scores',
                component: ExerciseScoresComponent,
                data: {
                    authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                    pageTitle: 'artemisApp.instructorDashboard.exerciseDashboard',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':exerciseId/participations',
                component: ParticipationComponent,
                data: {
                    authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.participation.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':exerciseId/plagiarism',
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
                path: ':exerciseId/grading/:tab',
                component: ProgrammingExerciseConfigureGradingComponent,
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.programmingExercise.home.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':exerciseId/exercise-statistics',
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
                path: ':exerciseId/exercise-hints/code-hint-management',
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
                path: ':exerciseId/edit-build-plan',
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
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingExerciseManagementRoutingModule {}
