import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { Observable, of } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { ExerciseHintComponent } from './exercise-hint.component';
import { ExerciseHintDetailComponent } from './exercise-hint-detail.component';
import { ExerciseHintUpdateComponent } from './exercise-hint-update.component';
import { ExerciseHintDeletePopupComponent } from './exercise-hint-delete-dialog.component';
import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

@Injectable({ providedIn: 'root' })
export class ExerciseHintResolve implements Resolve<IExerciseHint | null> {
    constructor(private service: ExerciseHintService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<IExerciseHint | null> {
        const id = route.params['id'] ? route.params['id'] : null;
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
    {
        path: 'exercise/:exerciseId/hints',
        component: ExerciseHintComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exerciseHint.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercise/:exerciseId/hints/:id/view',
        component: ExerciseHintDetailComponent,
        resolve: {
            exerciseHint: ExerciseHintResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exerciseHint.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercise/:exerciseId/hints/new',
        component: ExerciseHintUpdateComponent,
        resolve: {
            exerciseHint: ExerciseHintResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exerciseHint.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercise/:exerciseId/hints/:id/edit',
        component: ExerciseHintUpdateComponent,
        resolve: {
            exerciseHint: ExerciseHintResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exerciseHint.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const exerciseHintPopupRoute: Routes = [
    {
        path: 'exercise/:exerciseId/hints/:id/delete',
        component: ExerciseHintDeletePopupComponent,
        resolve: {
            exerciseHint: ExerciseHintResolve,
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exerciseHint.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
