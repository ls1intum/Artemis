import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { Injectable, inject } from '@angular/core';
import { ProofExerciseService } from 'app/proof/manage/service/proof-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

@Injectable({ providedIn: 'root' })
export class ProofExerciseResolver implements Resolve<ProofExercise> {
    private proofExerciseService = inject(ProofExerciseService);
    private courseService = inject(CourseManagementService);
    private exerciseGroupService = inject(ExerciseGroupService);

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.proofExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((proofExercise: HttpResponse<ProofExercise>) => proofExercise.body!),
            );
        } else if (route.params['courseId']) {
            if (route.params['examId'] && route.params['exerciseGroupId']) {
                return this.exerciseGroupService.find(route.params['courseId'], route.params['examId'], route.params['exerciseGroupId']).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new ProofExercise(undefined, exerciseGroup.body || undefined)),
                );
            } else {
                return this.courseService.find(route.params['courseId']).pipe(
                    filter((res) => !!res.body),
                    map((course: HttpResponse<Course>) => new ProofExercise(course.body || undefined, undefined)),
                );
            }
        }
        return of(new ProofExercise(undefined, undefined));
    }
}
