import { Routes, Resolve, RouterStateSnapshot, ActivatedRouteSnapshot } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExerciseDeletePopupComponent } from './file-upload-exercise-delete-dialog.component';

import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs/operators';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-update.component';

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseResolve implements Resolve<FileUploadExercise> {
    constructor(private service: FileUploadExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((fileUploadExercise: HttpResponse<FileUploadExercise>) => fileUploadExercise.body!));
        }
        return Observable.of(new FileUploadExercise());
    }
}

export const fileUploadExerciseRoute: Routes = [
    {
        path: 'file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercise/:exerciseId',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise/new',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercise/:exerciseId/edit',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise/:id',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const fileUploadExercisePopupRoute: Routes = [
    {
        path: 'file-upload-exercise/:id/delete',
        component: FileUploadExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
