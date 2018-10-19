import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Resolve, ActivatedRouteSnapshot, RouterStateSnapshot, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { FileUploadExercise } from 'app/shared/model/file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExerciseUpdateComponent } from './file-upload-exercise-update.component';
import { FileUploadExerciseDeletePopupComponent } from './file-upload-exercise-delete-dialog.component';
import { IFileUploadExercise } from 'app/shared/model/file-upload-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseResolve implements Resolve<IFileUploadExercise> {
    constructor(private service: FileUploadExerciseService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(id).pipe(map((fileUploadExercise: HttpResponse<FileUploadExercise>) => fileUploadExercise.body));
        }
        return of(new FileUploadExercise());
    }
}

export const fileUploadExerciseRoute: Routes = [
    {
        path: 'file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-exercise/:id/view',
        component: FileUploadExerciseDetailComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-exercise/new',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'file-upload-exercise/:id/edit',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const fileUploadExercisePopupRoute: Routes = [
    {
        path: 'file-upload-exercise/:id/delete',
        component: FileUploadExerciseDeletePopupComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve
        },
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
