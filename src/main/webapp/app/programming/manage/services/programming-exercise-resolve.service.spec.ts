import { beforeEach, describe, expect, it, vi } from 'vitest';
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
    let findForExamSpy: ReturnType<typeof vi.fn>;
    let findSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        findSpy = vi.fn();
        findForExamSpy = vi.fn();
        const programmingExerciseServiceSpy = {
            find: findSpy,
            findForExam: findForExamSpy,
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
            pathFromRoot: [{ params: {} }],
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
    it('should resolve an exam exercise from ancestor context and retain plagiarism options', async () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 123;
        findForExamSpy.mockReturnValue(of(new HttpResponse({ body: exercise })));
        findSpy.mockReturnValue(of(new HttpResponse({ body: exercise })));
        const route = new ActivatedRouteSnapshot();
        route.params = { exerciseId: 123 };
        const ancestor = new ActivatedRouteSnapshot();
        ancestor.params = { examId: 7 };
        vi.spyOn(route, 'pathFromRoot', 'get').mockReturnValue([ancestor, route]);
        expect(await firstValueFrom(resolver.resolve(route))).toBe(exercise);
        expect(findForExamSpy).toHaveBeenCalledExactlyOnceWith(123, true);
        expect(findSpy).not.toHaveBeenCalled();
    });

    it.each([
        ['true', false],
        ['false', true],
        [undefined, true],
    ])('uses import source flag %s before target exam context %s after reload', (sourceFlag, targetIsExam) => {
        const detailService = TestBed.inject(ProgrammingExerciseService);
        const exercise = new ProgrammingExercise(undefined, undefined);
        const courseFind = vi.spyOn(detailService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const examFind = vi.spyOn(detailService, 'findForExam').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const snapshot = new ActivatedRouteSnapshot();
        snapshot.params = { exerciseId: 123, ...(targetIsExam ? { examId: 7 } : {}) };
        snapshot.queryParams = sourceFlag === undefined ? {} : { sourceIsExamExercise: sourceFlag };
        vi.spyOn(snapshot, 'routeConfig', 'get').mockReturnValue({ path: 'exercises/:exerciseId/import' });
        vi.spyOn(snapshot, 'pathFromRoot', 'get').mockReturnValue([snapshot]);
        resolver.resolve(snapshot).subscribe((result) => expect(result).toBe(exercise));
        expect(sourceFlag === 'true' ? examFind : courseFind).toHaveBeenCalledExactlyOnceWith(123, true);
        expect(sourceFlag === 'true' ? courseFind : examFind).not.toHaveBeenCalled();
    });
});
