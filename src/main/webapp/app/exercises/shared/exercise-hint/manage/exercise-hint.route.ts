import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { ExerciseHintService } from '../shared/exercise-hint.service';
import { ExerciseHintComponent } from './exercise-hint.component';
import { ExerciseHintDetailComponent } from './exercise-hint-detail.component';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';
import { ExerciseHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ProgrammingExerciseResolve } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';

@Injectable({ providedIn: 'root' })
export class ExerciseHintResolve implements Resolve<ExerciseHint> {
    constructor(private service: ExerciseHintService) {}

    /**
     * Resolves the route into an exercise hint id and fetches it from the server
     * @param route Route which to resolve
     */
    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = route.params['exerciseId'] ? route.params['exerciseId'] : undefined;
        const hintId = route.params['hintId'] ? route.params['hintId'] : undefined;
        if (exerciseId && hintId) {
            return this.service.find(exerciseId, hintId).pipe(
                filter((response: HttpResponse<ExerciseHint>) => response.ok),
                map((exerciseHint: HttpResponse<ExerciseHint>) => exerciseHint.body!),
            );
        }
        return of(new ExerciseHint());
    }
}

export const exerciseHintRoute: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/exercise-hints/new',
            component: ExerciseHintUpdateComponent,
            resolve: {
                exercise: ProgrammingExerciseResolve,
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/exercise-hints/:hintId',
            component: ExerciseHintDetailComponent,
            resolve: {
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/exercise-hints/:hintId/edit',
            component: ExerciseHintUpdateComponent,
            resolve: {
                exercise: ProgrammingExerciseResolve,
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/exercise-hints',
            component: ExerciseHintComponent,
            resolve: {
                exercise: ProgrammingExerciseResolve,
            },
            data: {
                authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
