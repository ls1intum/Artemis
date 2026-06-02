import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExerciseResolve } from './programming-exercise-resolve.service';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

describe('ProgrammingExerciseResolve', () => {
    setupTestBed({ zoneless: true });

    let resolver: ProgrammingExerciseResolve;
    let findSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        findSpy = vi.fn();
        const programmingExerciseServiceSpy = {
            find: findSpy,
        };

        TestBed.configureTestingModule({
            providers: [ProgrammingExerciseResolve, { provide: ProgrammingExerciseService, useValue: programmingExerciseServiceSpy }],
        });

        resolver = TestBed.inject(ProgrammingExerciseResolve);
    });

    it('should be created', () => {
        expect(resolver).toBeTruthy();
    });

    it('should resolve programming exercise when exerciseId is provided', async () => {
        const exerciseId = 123;
        const mockExercise = new ProgrammingExercise(undefined, undefined);
        mockExercise.id = exerciseId;

        const mockResponse = new HttpResponse({
            body: mockExercise,
        });

        findSpy.mockReturnValue(of(mockResponse));

        const route = {
            params: { exerciseId: exerciseId },
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toEqual(mockExercise);
        expect(findSpy).toHaveBeenCalledWith(exerciseId, true);
    });

    it('should return new programming exercise when exerciseId is not provided', async () => {
        const route = {
            params: {},
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toBeInstanceOf(ProgrammingExercise);
        expect(result?.id).toBeUndefined();
        expect(findSpy).not.toHaveBeenCalled();
    });

    it('should return new programming exercise when exerciseId is undefined', async () => {
        const route = {
            params: { exerciseId: undefined },
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toBeInstanceOf(ProgrammingExercise);
        expect(result?.id).toBeUndefined();
        expect(findSpy).not.toHaveBeenCalled();
    });
});
