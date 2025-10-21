import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Injectable, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { UMLDiagramType } from '@ls1intum/apollon';

@Injectable({ providedIn: 'root' })
export class ModelingExerciseResolver implements Resolve<ModelingExercise> {
    private modelingExerciseService = inject(ModelingExerciseService);
    private courseService = inject(CourseManagementService);
    private exerciseGroupService = inject(ExerciseGroupService);

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.modelingExerciseService.find(route.params['exerciseId'], true).pipe(
                filter((res) => !!res.body),
                map((modelingExercise: HttpResponse<ModelingExercise>) => modelingExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup.body || undefined)),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => new ModelingExercise(UMLDiagramType.ClassDiagram, course.body || undefined, undefined)),
                );
            }
        }
        return of(new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined));
    }
}
