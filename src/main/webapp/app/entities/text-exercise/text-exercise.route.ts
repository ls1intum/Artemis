import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { TextExercise } from 'app/shared/model/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { TextExerciseComponent } from './text-exercise.component';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExerciseUpdateComponent } from './text-exercise-update.component';
import { TextExerciseDeletePopupComponent } from './text-exercise-delete-dialog.component';
import { ITextExercise } from 'app/shared/model/text-exercise.model';

@Injectable({ providedIn: 'root' })
export class TextExerciseResolve implements Resolve<ITextExercise> {
    constructor(private service: TextExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((textExercise: HttpResponse<TextExercise>) => textExercise.body));
        }
        return of(new TextExercise());
    }
}

export const textExerciseRoute: Routes = [
    {
        path: 'text-exercise',
        component: TextExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-exercise/:id/view',
        component: TextExerciseDetailComponent,
        resolve: {
            textExercise: TextExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-exercise/new',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text-exercise/:id/edit',
        component: TextExerciseUpdateComponent,
        resolve: {
            textExercise: TextExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const textExercisePopupRoute: Routes = [
    {
        path: 'text-exercise/:id/delete',
        component: TextExerciseDeletePopupComponent,
        resolve: {
            textExercise: TextExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
