import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextExerciseComponent } from './text-exercise.component';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExerciseUpdateComponent } from './text-exercise-update.component';
import { TextExercise } from './text-exercise.model';
import { Injectable } from '@angular/core';
import { TextExerciseService } from 'app/entities/text-exercise/text-exercise.service';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class TextExerciseResolver implements Resolve<TextExercise> {
    constructor(private textExerciseService: TextExerciseService, private courseService: CourseService) {}

    /**
     * Resolves the route and initializes text exercise
     * @param route
     * @param state
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        if (route.params['exerciseId']) {
            return this.textExerciseService.find(route.params['exerciseId']).pipe(
                filter(res => !!res.body),
                map((textExercise: HttpResponse<TextExercise>) => textExercise.body!),
            );
        } else if (route.params['courseId']) {
            return this.courseService.find(route.params['courseId']).pipe(
                filter(res => !!res.body),
                map((course: HttpResponse<Course>) => new TextExercise(course.body!)),
            );
        }
        return Observable.of(new TextExercise());
    }
}

export const textExerciseRoute: Routes = [
    {
        path: 'text-exercise/:id',
        component: TextExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise/new',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercise/:exerciseId/edit',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise',
        component: TextExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise/:id',
        component: TextExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
