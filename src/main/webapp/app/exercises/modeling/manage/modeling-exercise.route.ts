import { Injectable, NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterModule, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ModelingExerciseComponent } from './modeling-exercise.component';
import { ModelingExerciseDetailComponent } from './modeling-exercise-detail.component';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { CourseManagementService } from '../../../course/manage/course-management.service';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { UMLDiagramType } from '@ls1intum/apollon';

@Injectable({ providedIn: 'root' })
export class ModelingExerciseResolver implements Resolve<ModelingExercise> {
    constructor(private modelingExerciseService: ModelingExerciseService, private courseService: CourseManagementService) {}
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        if (route.params['exerciseId']) {
            return this.modelingExerciseService.find(route.params['exerciseId']).pipe(
                filter(res => !!res.body),
                map((modelingExercise: HttpResponse<ModelingExercise>) => modelingExercise.body!),
            );
        } else if (route.params['courseId']) {
            return this.courseService.find(route.params['courseId']).pipe(
                filter(res => !!res.body),
                map((course: HttpResponse<Course>) => new ModelingExercise(UMLDiagramType.ClassDiagram, course.body!)),
            );
        }
        return Observable.of(new ModelingExercise(UMLDiagramType.ClassDiagram));
    }
}

const routes: Routes = [
    {
        path: 'course-management/:courseId/modeling-exercises/new',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/modeling-exercises/:exerciseId/edit',
        component: ModelingExerciseUpdateComponent,
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/modeling-exercises/:exerciseId',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course-management/:courseId/modeling-exercises',
        component: ModelingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisModelingExerciseRoutingModule {}
