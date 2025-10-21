import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExerciseResolve } from './programming-exercise-resolve.service';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

describe('ProgrammingExerciseResolve', () => {
    let resolver: ProgrammingExerciseResolve;
    let mockProgrammingExerciseService: jest.Mocked<ProgrammingExerciseService>;

    beforeEach(() => {
        const programmingExerciseServiceSpy = {
            find: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [ProgrammingExerciseResolve, { provide: ProgrammingExerciseService, useValue: programmingExerciseServiceSpy }],
        });

        resolver = TestBed.inject(ProgrammingExerciseResolve);
        mockProgrammingExerciseService = TestBed.inject(ProgrammingExerciseService) as jest.Mocked<ProgrammingExerciseService>;
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

        mockProgrammingExerciseService.find.mockReturnValue(of(mockResponse));

        const route = {
            params: { exerciseId: exerciseId },
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toEqual(mockExercise);
        expect(mockProgrammingExerciseService.find).toHaveBeenCalledWith(exerciseId, true, true);
    });

    it('should return new programming exercise when exerciseId is not provided', async () => {
        const route = {
            params: {},
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toBeInstanceOf(ProgrammingExercise);
        expect(result?.id).toBeUndefined();
        expect(mockProgrammingExerciseService.find).not.toHaveBeenCalled();
    });

    it('should return new programming exercise when exerciseId is undefined', async () => {
        const route = {
            params: { exerciseId: undefined },
        } as unknown as ActivatedRouteSnapshot;

        const result = await firstValueFrom(resolver.resolve(route));
        expect(result).toBeInstanceOf(ProgrammingExercise);
        expect(result?.id).toBeUndefined();
        expect(mockProgrammingExerciseService.find).not.toHaveBeenCalled();
    });
});
