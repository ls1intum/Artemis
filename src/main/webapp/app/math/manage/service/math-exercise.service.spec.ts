import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

const BASE_URL = 'api/math/math-exercises';

describe('MathExerciseService', () => {
    setupTestBed({ zoneless: true });

    let service: MathExerciseService;
    let httpMock: HttpTestingController;

    const mockExercise = (): MathExercise => {
        const ex = new MathExercise(undefined);
        ex.id = 1;
        ex.title = 'Test Math';
        ex.description = 'Prove X';
        return ex;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MathExerciseService,
                {
                    provide: ExerciseService,
                    useValue: {
                        processExerciseEntityResponse: vi.fn((res) => res),
                        processExerciseEntityArrayResponse: vi.fn((res) => res),
                    },
                },
            ],
        });
        service = TestBed.inject(MathExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create an exercise', async () => {
        const exercise = mockExercise();
        exercise.id = undefined;

        const promise = service.create(exercise).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: BASE_URL });
        req.flush({ ...exercise, id: 42 });
        const response = await promise;

        expect(response?.body?.id).toBe(42);
    });

    it('should update an exercise', async () => {
        const exercise = mockExercise();

        const promise = service.update(exercise).toPromise();
        const req = httpMock.expectOne({ method: 'PUT', url: BASE_URL });
        req.flush(exercise);
        const response = await promise;

        expect(response?.body?.id).toBe(exercise.id);
    });

    it('should find an exercise by id', async () => {
        const exercise = mockExercise();

        const promise = service.find(exercise.id!).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: `${BASE_URL}/${exercise.id}` });
        req.flush(exercise);
        const response = await promise;

        expect(response?.body?.title).toBe(exercise.title);
    });

    it('should delete an exercise', async () => {
        const promise = service.delete(1).toPromise();
        const req = httpMock.expectOne({ method: 'DELETE', url: `${BASE_URL}/1` });
        req.flush(null);
        await promise;

        expect(req.request.method).toBe('DELETE');
    });

    it('should import an exercise', async () => {
        const source = mockExercise();
        const imported = new MathExercise(undefined);
        imported.title = 'Imported Math';

        const promise = service.import(source).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: `${BASE_URL}/import/${source.id}` });
        req.flush({ ...imported, id: 99 });
        const response = await promise;

        expect(response?.body?.id).toBe(99);
    });

    it('should set exercise type to MATH', () => {
        const ex = new MathExercise(undefined);
        expect(ex.type).toBe(ExerciseType.MATH);
    });
});
