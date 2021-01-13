import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Injectable, NgModule } from '@angular/core';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { map } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { CanDeactivateGuard } from 'app/shared/guard/can-deactivate.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { PlagiarismInspectorComponent } from 'app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseResolve implements Resolve<ProgrammingExercise> {
    constructor(private service: ProgrammingExerciseService) {}

    resolve(route: ActivatedRouteSnapshot) {
        const id = route.params['id'] ? route.params['id'] : undefined;
        if (id) {
            return this.service.find(id).pipe(map((programmingExercise: HttpResponse<ProgrammingExercise>) => programmingExercise.body!));
        }
        return Observable.of(new ProgrammingExercise(undefined, undefined));
    }
}

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises/new',
        component: ProgrammingExerciseUpdateComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
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
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
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
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:id',
        component: ProgrammingExerciseDetailComponent,
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:id/plagiarism',
        component: PlagiarismInspectorComponent,
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarism-detection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/grading/:tab',
        component: ProgrammingExerciseConfigureGradingComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [CanDeactivateGuard],
    },
    {
        path: ':courseId/programming-exercises',
        redirectTo: ':courseId/exercises',
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingExerciseManagementRoutingModule {}
