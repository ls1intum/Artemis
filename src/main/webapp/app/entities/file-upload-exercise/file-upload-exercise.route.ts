import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';

import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { filter, map } from 'rxjs/operators';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-update.component';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseResolve implements Resolve<FileUploadExercise> {
    constructor(private fileUploadExerciseService: FileUploadExerciseService, private courseService: CourseService) {}

    /**
     * Resolves the route and initializes file upload exercise either from exerciseId (existing exercise) or
     * from course id (new exercise)
     * @param route
     * @param state
     */
    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        if (route.params['exerciseId']) {
            return this.fileUploadExerciseService.find(route.params['exerciseId']).pipe(
                filter(res => !!res.body),
                map((fileUploadExercise: HttpResponse<FileUploadExercise>) => fileUploadExercise.body!),
            );
        } else if (route.params['courseId']) {
            return this.courseService.find(route.params['courseId']).pipe(
                filter(res => !!res.body),
                map((course: HttpResponse<Course>) => {
                    const fileUploadExercise = new FileUploadExercise(course.body!);
                    fileUploadExercise.filePattern = 'pdf, png';
                    return fileUploadExercise;
                }),
            );
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
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
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
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
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
        path: 'course/:courseId/file-upload-exercise/:exerciseId',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
