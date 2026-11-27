import { exerciseForImportFromFile } from 'app/exercise/import/import-from-file-navigation';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRouteSnapshot, Resolve, Router } from '@angular/router';
import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { filter, map, switchMap } from 'rxjs/operators';
import { Observable, from } from 'rxjs';

@Service()
export class ModelingExerciseResolver implements Resolve<ModelingExercise> {
    private modelingExerciseService = inject(ModelingExerciseService);
    private router = inject(Router);
    private courseService = inject(CourseManagementService);
    private exerciseGroupService = inject(ExerciseGroupService);

    /**
     * The diagram type of a new exercise. Apollon is loaded on first use and not imported at the top of the file: this resolver
     * is part of the exam management routes, which would otherwise download the whole modeling editor without ever showing a diagram.
     * Opening the page that creates a modeling exercise needs the editor right afterwards anyway.
     */
    private defaultDiagramType() {
        return from(import('@tumaet/apollon')).pipe(map(({ UMLDiagramType }) => UMLDiagramType.ClassDiagram));
    }

    resolve(route: ActivatedRouteSnapshot): Observable<ModelingExercise> {
        if (route.params['exerciseId']) {
            return this.modelingExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((modelingExercise: HttpResponse<ModelingExercise>) => modelingExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    switchMap((exerciseGroup: HttpResponse<ExerciseGroup>) =>
                        this.defaultDiagramType().pipe(
                            map(
                                (diagramType) =>
                                    exerciseForImportFromFile<ModelingExercise>(this.router, undefined, exerciseGroup.body!) ??
                                    new ModelingExercise(diagramType, undefined, exerciseGroup.body || undefined),
                            ),
                        ),
                    ),
                );
            } else {
                return this.courseService.findForManagement(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    switchMap((course: HttpResponse<Course>) =>
                        this.defaultDiagramType().pipe(
                            map(
                                (diagramType) =>
                                    exerciseForImportFromFile<ModelingExercise>(this.router, course.body!, undefined) ??
                                    new ModelingExercise(diagramType, course.body || undefined, undefined),
                            ),
                        ),
                    ),
                );
            }
        }
        return this.defaultDiagramType().pipe(map((diagramType) => new ModelingExercise(diagramType, undefined, undefined)));
    }
}
