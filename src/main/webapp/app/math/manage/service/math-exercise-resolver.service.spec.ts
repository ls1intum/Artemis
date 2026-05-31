import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRouteSnapshot } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { MathExerciseResolver } from 'app/math/manage/service/math-exercise-resolver.service';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';

describe('MathExerciseResolver', () => {
    setupTestBed({ zoneless: true });

    let resolver: MathExerciseResolver;
    let mathExerciseService: { find: ReturnType<typeof vi.fn> };
    let courseService: { find: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        mathExerciseService = { find: vi.fn() };
        courseService = { find: vi.fn() };
        TestBed.configureTestingModule({
            providers: [MathExerciseResolver, { provide: MathExerciseService, useValue: mathExerciseService }, { provide: CourseManagementService, useValue: courseService }],
        });
        resolver = TestBed.inject(MathExerciseResolver);
    });

    it('resolves an existing exercise via MathExerciseService when exerciseId is in the route', async () => {
        const ex = new MathExercise(undefined);
        ex.id = 42;
        mathExerciseService.find.mockReturnValue(of(new HttpResponse({ body: ex })));
        const snapshot = { params: { exerciseId: 42 }, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as MathExercise;

        expect(result.id).toBe(42);
        expect(mathExerciseService.find).toHaveBeenCalledWith(42);
    });

    it('resolves a new exercise bound to a course when courseId is in the route', async () => {
        const course = new Course();
        course.id = 7;
        courseService.find.mockReturnValue(of(new HttpResponse({ body: course })));
        const snapshot = { params: { courseId: '7' }, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as MathExercise;

        expect(result.course?.id).toBe(7);
        expect(courseService.find).toHaveBeenCalledWith(7);
    });

    it('returns a blank MathExercise when no params are present', async () => {
        const snapshot = { params: {}, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as MathExercise;

        expect(result).toBeInstanceOf(MathExercise);
        expect(result.id).toBeUndefined();
    });
});
