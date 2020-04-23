import { ActivatedRouteSnapshot, Resolve, RouterModule, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Injectable, NgModule } from '@angular/core';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseManageTestCasesComponent } from 'app/exercises/programming/manage/test-cases/programming-exercise-manage-test-cases.component';
import { CanDeactivateGuard } from 'app/shared/guard/can-deactivate.guard';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
    constructor(private service: ProgrammingExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body!));
        }
        return Observable.of(new ProgrammingExercise());
    }
}

const routes: Routes = [
    {
        path: ':courseId/programming-exercises/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:id/edit',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/import/:id',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/test-cases',
        component: ProgrammingExerciseManageTestCasesComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [CanDeactivateGuard],
    },
    {
        path: ':courseId/programming-exercises/:id',
        component: ProgrammingExerciseDetailComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingExerciseManagementRoutingModule {}
