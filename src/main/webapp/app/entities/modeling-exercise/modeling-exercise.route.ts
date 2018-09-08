import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ModelingExercise } from 'app/shared/model/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { ModelingExerciseComponent } from './modeling-exercise.component';
import { ModelingExerciseDetailComponent } from './modeling-exercise-detail.component';
import { ModelingExerciseUpdateComponent } from './modeling-exercise-update.component';
import { ModelingExerciseDeletePopupComponent } from './modeling-exercise-delete-dialog.component';
import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';

@Injectable({ providedIn: 'root' })
export class ModelingExerciseResolve implements Resolve<IModelingExercise> {
    constructor(private service: ModelingExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((modelingExercise: HttpResponse<ModelingExercise>) => modelingExercise.body));
        }
        return of(new ModelingExercise());
    }
}

export const modelingExerciseRoute: Routes = [
    {
        path: 'modeling-exercise',
        component: ModelingExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-exercise/:id/view',
        component: ModelingExerciseDetailComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-exercise/new',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'modeling-exercise/:id/edit',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const modelingExercisePopupRoute: Routes = [
    {
        path: 'modeling-exercise/:id/delete',
        component: ModelingExerciseDeletePopupComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
