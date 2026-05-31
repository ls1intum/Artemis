import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { Injectable, inject } from '@angular/core';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { Course } from 'app/course/shared/entities/course.model';

@Injectable({ providedIn: 'root' })
export class MathExerciseResolver implements Resolve<MathExercise> {
    private mathExerciseService = inject(MathExerciseService);
    private courseService = inject(CourseManagementService);

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['exerciseId']) {
            return this.mathExerciseService.find(route.params['exerciseId']).pipe(
                filter((res) => !!res.body),
                map((mathExercise: HttpResponse<MathExercise>) => mathExercise.body!),
            );
        }
        const courseId = Number(this.findParam(route, 'courseId'));
        if (courseId) {
            return this.courseService.find(courseId).pipe(
                filter((res) => !!res.body),
                map((course: HttpResponse<Course>) => new MathExercise(course.body || undefined)),
            );
        }
        return of(new MathExercise(undefined));
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
