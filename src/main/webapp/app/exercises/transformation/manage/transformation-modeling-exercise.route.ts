import { Injectable, NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TransformationModelingExerciseDetailComponent } from './transformation-modeling-exercise-detail.component';
import { TransformationModelingExerciseUpdateComponent } from 'app/exercises/transformation/manage/transformation-modeling-exercise-update.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExerciseResolver } from 'app/exercises/modeling/manage/modeling-exercise.route';

@Injectable({ providedIn: 'root' })
export class TransformationModelingExerciseResolver implements Resolve<TransformationModelingExercise> {
    constructor(
        private transformationModelingExerciseService: ModelingExerciseService,
        private courseService: CourseManagementService,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.transformationModelingExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((modelingExercise: HttpResponse<TransformationModelingExercise>) => modelingExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map(
                        (exerciseGroup: HttpResponse<ExerciseGroup>) =>
                            new TransformationModelingExercise(UMLDiagramType.PetriNet, UMLDiagramType.ReachabilityGraph, undefined, exerciseGroup.body || undefined),
                    ),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map(
                        (course: HttpResponse<Course>) =>
                            new TransformationModelingExercise(UMLDiagramType.PetriNet, UMLDiagramType.ReachabilityGraph, course.body || undefined, undefined),
                    ),
                );
            }
        }
        return of(new TransformationModelingExercise(UMLDiagramType.PetriNet, UMLDiagramType.ReachabilityGraph, undefined, undefined));
    }
}

export const routes: Routes = [
    {
        path: ':courseId/transformation-modeling-exercises/new',
        component: TransformationModelingExerciseUpdateComponent,
        resolve: {
            transformationModelingExercise: TransformationModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/transformation-modeling-exercises/:exerciseId/edit',
        component: TransformationModelingExerciseUpdateComponent,
        resolve: {
            transformationModelingExercise: TransformationModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/transformation-modeling-exercises/:exerciseId',
        component: TransformationModelingExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/transformation-modeling-exercises/:exerciseId/example-submissions',
        component: ExampleSubmissionsComponent,
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/transformation-modeling-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/transformation-modeling-exercises/:exerciseId/exercise-statistics',
        component: ExerciseStatisticsComponent,
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisTransformationModelingExerciseRoutingModule {}
