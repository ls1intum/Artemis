import { ActivatedRouteSnapshot, Resolve, RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Injectable, NgModule } from '@angular/core';
import { filter, map } from 'rxjs/operators';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseResolve implements Resolve<FileUploadExercise> {
    constructor(private fileUploadExerciseService: FileUploadExerciseService, private courseService: CourseManagementService, private exerciseGroupService: ExerciseGroupService) {}

    /**
     * Resolves the route and initializes file upload exercise either from exerciseId (existing exercise) or
     * from course id (new exercise)
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.fileUploadExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((fileUploadExercise: HttpResponse<FileUploadExercise>) => fileUploadExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => {
                        const fileUploadExercise = new FileUploadExercise(undefined, exerciseGroup.body!);
                        fileUploadExercise.filePattern = 'pdf, png';
                        return fileUploadExercise;
                    }),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => {
                        const fileUploadExercise = new FileUploadExercise(course.body!, undefined);
                        fileUploadExercise.filePattern = 'pdf, png';
                        return fileUploadExercise;
                    }),
                );
            }
        }
        return of(new FileUploadExercise(undefined, undefined));
    }
}

const routes: Routes = [
    {
        path: ':courseId/file-upload-exercises/new',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/edit',
        component: FileUploadExerciseUpdateComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/exercise-statistics',
        component: ExerciseStatisticsComponent,
        resolve: {
            fileUploadExercise: FileUploadExerciseResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisFileUploadExerciseManagementRoutingModule {}
