import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProgrammingExercise } from 'app/shared/model/programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ProgrammingExerciseComponent } from './programming-exercise.component';
import { ProgrammingExerciseDetailComponent } from './programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from './programming-exercise-update.component';
import { ProgrammingExerciseDeletePopupComponent } from './programming-exercise-delete-dialog.component';
import { IProgrammingExercise } from 'app/shared/model/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<IProgrammingExercise> {
    constructor(private service: ProgrammingExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body));
        }
        return of(new ProgrammingExercise());
    }
}

export const programmingExerciseRoute: Routes = [
    {
        path: 'programming-exercise',
        component: ProgrammingExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-exercise/:id/view',
        component: ProgrammingExerciseDetailComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-exercise/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'programming-exercise/:id/edit',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const programmingExercisePopupRoute: Routes = [
    {
        path: 'programming-exercise/:id/delete',
        component: ProgrammingExerciseDeletePopupComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
