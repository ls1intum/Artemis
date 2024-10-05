import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { filter, map, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class FileUploadExerciseManagementResolve implements Resolve<FileUploadExercise> {
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private courseService = inject(CourseManagementService);
    private exerciseGroupService = inject(ExerciseGroupService);

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
