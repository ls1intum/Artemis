import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRouteSnapshot } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { ProofExerciseResolver } from 'app/proof/manage/service/proof-exercise-resolver.service';
import { ProofExerciseService } from 'app/proof/manage/service/proof-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';

describe('ProofExerciseResolver', () => {
    setupTestBed({ zoneless: true });

    let resolver: ProofExerciseResolver;
    let proofExerciseService: { find: ReturnType<typeof vi.fn> };
    let courseService: { find: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        proofExerciseService = { find: vi.fn() };
        courseService = { find: vi.fn() };
        TestBed.configureTestingModule({
            providers: [ProofExerciseResolver, { provide: ProofExerciseService, useValue: proofExerciseService }, { provide: CourseManagementService, useValue: courseService }],
        });
        resolver = TestBed.inject(ProofExerciseResolver);
    });

    it('resolves an existing exercise via ProofExerciseService when exerciseId is in the route', async () => {
        const ex = new ProofExercise(undefined);
        ex.id = 42;
        proofExerciseService.find.mockReturnValue(of(new HttpResponse({ body: ex })));
        const snapshot = { params: { exerciseId: 42 }, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as ProofExercise;

        expect(result.id).toBe(42);
        expect(proofExerciseService.find).toHaveBeenCalledWith(42);
    });

    it('resolves a new exercise bound to a course when courseId is in the route', async () => {
        const course = new Course();
        course.id = 7;
        courseService.find.mockReturnValue(of(new HttpResponse({ body: course })));
        const snapshot = { params: { courseId: '7' }, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as ProofExercise;

        expect(result.course?.id).toBe(7);
        expect(courseService.find).toHaveBeenCalledWith(7);
    });

    it('returns a blank ProofExercise when no params are present', async () => {
        const snapshot = { params: {}, parent: null } as unknown as ActivatedRouteSnapshot;

        const result = (await firstValueFrom(resolver.resolve(snapshot) as any)) as ProofExercise;

        expect(result).toBeInstanceOf(ProofExercise);
        expect(result.id).toBeUndefined();
    });
});
