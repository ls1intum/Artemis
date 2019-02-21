import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ProgrammingExerciseComponent } from './programming-exercise.component';
import { ProgrammingExerciseDetailComponent } from './programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from './programming-exercise-update.component';
import { ProgrammingExerciseDeletePopupComponent } from './programming-exercise-delete-dialog.component';
import { ProgrammingExercisePopupComponent } from './programming-exercise-dialog.component';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
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
        path: 'course/:courseId/programming-exercise',
        component: ProgrammingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/programming-exercise/:id/view',
        component: ProgrammingExerciseDetailComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/programming-exercise/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/programming-exercise/:id/edit',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const programmingExercisePopupRoute: Routes = [
    {
        path: 'course/:courseId/programming-exercise-new',
        component: ProgrammingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'course/:courseId/programming-exercise/:id/editlink',
        component: ProgrammingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'course/:courseId/programming-exercise/:id/delete',
        component: ProgrammingExerciseDeletePopupComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
