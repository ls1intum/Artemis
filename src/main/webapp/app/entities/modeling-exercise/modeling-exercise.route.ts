import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ModelingExerciseComponent } from './modeling-exercise.component';
import { ModelingExerciseDetailComponent } from './modeling-exercise-detail.component';
import { ModelingExerciseUpdateComponent } from 'app/entities/modeling-exercise/modeling-exercise-update.component';
import { Injectable } from '@angular/core';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ModelingExerciseService } from 'app/entities/modeling-exercise/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ModelingExerciseResolver implements Resolve<ModelingExercise> {
    constructor(private modelingExerciseService: ModelingExerciseService, private courseService: CourseService) {}
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        if (route.params['exerciseId']) {
            return this.modelingExerciseService.find(route.params['exerciseId']).pipe(
                filter(res => !!res.body),
                map((modelingExercise: HttpResponse<ModelingExercise>) => modelingExercise.body!),
            );
        } else if (route.params['courseId']) {
            return this.courseService.find(route.params['courseId']).pipe(
                filter(res => !!res.body),
                map((course: HttpResponse<Course>) => new ModelingExercise('ClassDiagram', course.body!)),
            );
        }
        return Observable.of(new ModelingExercise('ClassDiagram'));
    }
}

export const modelingExerciseRoute: Routes = [
    {
        path: 'modeling-exercise/:id',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/modeling-exercise/new',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercise/:exerciseId/edit',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/modeling-exercise',
        component: ModelingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/modeling-exercise/:id',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
