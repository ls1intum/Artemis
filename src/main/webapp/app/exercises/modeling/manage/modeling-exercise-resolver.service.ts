import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ModelingExerciseResolver implements Resolve<ModelingExercise> {
    constructor(private modelingExerciseService: ModelingExerciseService, private courseService: CourseManagementService, private exerciseGroupService: ExerciseGroupService) {}

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.modelingExerciseService.find(route.params['exerciseId']).pipe(
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
