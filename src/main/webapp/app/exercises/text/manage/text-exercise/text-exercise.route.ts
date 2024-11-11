import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExerciseUpdateComponent } from './text-exercise-update.component';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { Injectable } from '@angular/core';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';

@Injectable({ providedIn: 'root' })
export class TextExerciseResolver implements Resolve<TextExercise> {
    constructor(
        private textExerciseService: TextExerciseService,
        private courseService: CourseManagementService,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    /**
     * Resolves the route and initializes text exercise
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.textExerciseService.find(route.params['exerciseId'], true).pipe(
                filter((res) => !!res.body),
                map((textExercise: HttpResponse<TextExercise>) => textExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new TextExercise(undefined, exerciseGroup.body || undefined)),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => new TextExercise(course.body || undefined, undefined)),
                );
            }
        }
        return of(new TextExercise(undefined, undefined));
    }
}

export const textExerciseRoute: Routes = [
    {
        path: ':courseId/text-exercises/new',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId',
        component: TextExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId/edit',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId/import',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId/plagiarism',
        component: PlagiarismInspectorComponent,
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId/example-submissions',
        component: ExampleSubmissionsComponent,
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/text-exercises/:exerciseId/iris-settings',
        loadChildren: () =>
            import('app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update-routing.module').then((m) => m.IrisExerciseSettingsUpdateRoutingModule),
    },
    {
        path: ':courseId/text-exercises/:exerciseId/exercise-statistics',
        component: ExerciseStatisticsComponent,
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId/tutor-effort-statistics',
        loadChildren: () => import('../tutor-effort/tutor-effort-statistics.module').then((m) => m.ArtemisTutorEffortStatisticsModule),
    },
];
