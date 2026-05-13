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
        }
        const courseId = Number(this.findParam(route, 'courseId'));
        if (courseId) {
            const examId = Number(this.findParam(route, 'examId'));
            const exerciseGroupId = Number(this.findParam(route, 'exerciseGroupId'));
            if (examId && exerciseGroupId) {
                return this.exerciseGroupService.find(courseId, examId, exerciseGroupId).pipe(
                    filter((res) => !!res.body),
                    map((exerciseGroup: HttpResponse<ExerciseGroup>) => new ProofExercise(undefined, exerciseGroup.body || undefined)),
                );
            }
            return this.courseService.find(courseId).pipe(
                filter((res) => !!res.body),
                map((course: HttpResponse<Course>) => new ProofExercise(course.body || undefined, undefined)),
            );
        }
        return of(new ProofExercise(undefined, undefined));
    }

    private findParam(route: ActivatedRouteSnapshot, key: string): string | undefined {
        let current: ActivatedRouteSnapshot | null = route;
        while (current) {
            if (current.params[key]) return current.params[key];
            current = current.parent;
        }
        return undefined;
    }
}
