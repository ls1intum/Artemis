import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';
import { ExerciseResultComponent } from './exercise-result.component';
import { ExerciseResultDetailComponent } from './exercise-result-detail.component';
import { ExerciseResultUpdateComponent } from './exercise-result-update.component';
import { ExerciseResultDeletePopupComponent } from './exercise-result-delete-dialog.component';
import { IExerciseResult } from 'app/shared/model/exercise-result.model';

@Injectable({ providedIn: 'root' })
export class ExerciseResultResolve implements Resolve<IExerciseResult> {
    constructor(private service: ExerciseResultService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((exerciseResult: HttpResponse<ExerciseResult>) => exerciseResult.body));
        }
        return of(new ExerciseResult());
    }
}

export const exerciseResultRoute: Routes = [
    {
        path: 'exercise-result',
        component: ExerciseResultComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exerciseResult.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'exercise-result/:id/view',
        component: ExerciseResultDetailComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exerciseResult.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'exercise-result/new',
        component: ExerciseResultUpdateComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exerciseResult.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'exercise-result/:id/edit',
        component: ExerciseResultUpdateComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exerciseResult.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const exerciseResultPopupRoute: Routes = [
    {
        path: 'exercise-result/:id/delete',
        component: ExerciseResultDeletePopupComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exerciseResult.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
