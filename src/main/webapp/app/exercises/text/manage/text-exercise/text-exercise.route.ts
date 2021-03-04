import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExerciseUpdateComponent } from './text-exercise-update.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Injectable } from '@angular/core';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';

@Injectable({ providedIn: 'root' })
export class TextExerciseResolver implements Resolve<TextExercise> {
    constructor(private textExerciseService: TextExerciseService, private courseService: CourseManagementService, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Resolves the route and initializes text exercise
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.textExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((textExercise: HttpResponse<TextExercise>) => textExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['groupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['groupId']).pipe(
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
        return Observable.of(new TextExercise(undefined, undefined));
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
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises/:exerciseId',
        component: TextExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
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
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarism-detection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/text-exercises',
        redirectTo: ':courseId/exercises',
    },
];
