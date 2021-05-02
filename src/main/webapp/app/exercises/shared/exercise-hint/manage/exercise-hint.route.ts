import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { ExerciseHintService } from './exercise-hint.service';
import { ExerciseHintComponent } from './exercise-hint.component';
import { ExerciseHintDetailComponent } from './exercise-hint-detail.component';
import { ExerciseHintUpdateComponent } from './exercise-hint-update.component';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

@Injectable({ providedIn: 'root' })
export class ExerciseHintResolve implements Resolve<ExerciseHint | null> {
    constructor(private service: ExerciseHintService) {}

    /**
     * Resolves the route into an exercise hint id and fetches it from the server
     * @param route Route which to resolve
     */
    resolve(route: ActivatedRouteSnapshot): Observable<ExerciseHint | null> {
        const id = route.params['hintId'] ? route.params['hintId'] : undefined;
        if (id) {
            return this.service.find(id).pipe(
                filter((response: HttpResponse<ExerciseHint>) => response.ok),
                map((exerciseHint: HttpResponse<ExerciseHint>) => exerciseHint.body),
            );
        }
        return of(new ExerciseHint());
    }
}

export const exerciseHintRoute: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/hints/new',
            component: ExerciseHintUpdateComponent,
            resolve: {
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/hints/:hintId',
            component: ExerciseHintDetailComponent,
            resolve: {
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/hints/:hintId/edit',
            component: ExerciseHintUpdateComponent,
            resolve: {
                exerciseHint: ExerciseHintResolve,
            },
            data: {
                authorities: [Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/hints',
            component: ExerciseHintComponent,
            data: {
                authorities: [Authority.ADMIN],
                pageTitle: 'artemisApp.exerciseHint.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
