import { ActivatedRouteSnapshot, Resolve } from '@angular/router';

import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Injectable, inject } from '@angular/core';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Injectable({ providedIn: 'root' })
export class TextExerciseResolver implements Resolve<TextExercise> {
    private textExerciseService = inject(TextExerciseService);
    private courseService = inject(CourseManagementService);
    private exerciseGroupService = inject(ExerciseGroupService);

    /**
     * Resolves the route and initializes text exercise
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.textExerciseService.find(route.params['exerciseId'], true, true).pipe(
                filter((res) => !!res.body),
                map((textExercise: HttpResponse<TextExercise>) => textExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new TextExercise(undefined, exerciseGroup.body || undefined)),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => new TextExercise(course.body || undefined, undefined)),
                );
            }
        }
        return of(new TextExercise(undefined, undefined));
    }
}
