import { of } from 'rxjs';
import { Injectable } from '@angular/core';
import { filter, map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MathExercise } from 'app/entities/math-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Authority } from 'app/shared/constants/authority.constants';

import { MathExerciseManageService } from './math-exercise-manage.service';
import { MathExerciseDetailComponent } from './math-exercise-detail.component';
import { MathExerciseComposeComponent } from './math-exercise-compose.component';
import { MathExerciseEditComponent } from 'app/exercises/math/manage/math-exercise-edit.component';

@Injectable({ providedIn: 'root' })
export class MathExerciseResolver implements Resolve<MathExercise> {
    constructor(
        private mathExerciseService: MathExerciseManageService,
        private courseService: CourseManagementService,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    /**
     * Resolves the route and initializes math exercise
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.mathExerciseService.find(route.params['exerciseId'], true).pipe(
                filter((res) => !!res.body),
                map((mathExercise: HttpResponse<MathExercise>) => mathExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new MathExercise(undefined, exerciseGroup.body || undefined)),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => new MathExercise(course.body || undefined, undefined)),
                );
            }
        }
        return of(new MathExercise(undefined, undefined));
    }
}

export const mathExerciseManageRoute: Routes = [
    {
        path: ':courseId/math-exercises/new',
        component: MathExerciseEditComponent,
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/math-exercises/:exerciseId/compose',
        component: MathExerciseComposeComponent,
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/math-exercises/:exerciseId',
        component: MathExerciseDetailComponent,
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/math-exercises/:exerciseId/edit',
        component: MathExerciseEditComponent,
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/math-exercises/:exerciseId/import',
        component: MathExerciseEditComponent,
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.mathExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/math-exercises',
        redirectTo: ':courseId/exercises',
    },
];
