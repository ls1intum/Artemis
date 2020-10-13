import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, Routes, Router } from '@angular/router';
import { Observable, of, EMPTY } from 'rxjs';
import { flatMap } from 'rxjs/operators';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IExerciseResult, ExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';
import { ExerciseResultComponent } from './exercise-result.component';
import { ExerciseResultDetailComponent } from './exercise-result-detail.component';
import { ExerciseResultUpdateComponent } from './exercise-result-update.component';

@Injectable({ providedIn: 'root' })
export class ExerciseResultResolve implements Resolve<IExerciseResult> {
    constructor(private service: ExerciseResultService, private router: Router) {}

    resolve(route: ActivatedRouteSnapshot): Observable<IExerciseResult> | Observable<never> {
        const id = route.params['id'];
        if (id) {
            return this.service.find(id).pipe(
                flatMap((exerciseResult: HttpResponse<ExerciseResult>) => {
                    if (exerciseResult.body) {
                        return of(exerciseResult.body);
                    } else {
                        this.router.navigate(['404']);
                        return EMPTY;
                    }
                }),
            );
        }
        return of(new ExerciseResult());
    }
}

export const exerciseResultRoute: Routes = [
    {
        path: '',
        component: ExerciseResultComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exerciseResult.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/view',
        component: ExerciseResultDetailComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exerciseResult.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'new',
        component: ExerciseResultUpdateComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exerciseResult.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':id/edit',
        component: ExerciseResultUpdateComponent,
        resolve: {
            exerciseResult: ExerciseResultResolve,
        },
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exerciseResult.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
