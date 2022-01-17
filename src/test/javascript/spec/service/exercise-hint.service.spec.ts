import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockExerciseService } from '../helpers/mocks/service/mock-exercise.service';

describe('ExerciseHint Service', () => {
    let service: ExerciseHintService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseHint;
    let exerciseHint: ExerciseHint;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: ExerciseService, useClass: MockExerciseService }],
        });
        expectedResult = {} as HttpResponse<ExerciseHint>;
        service = TestBed.inject(ExerciseHintService);
        httpMock = TestBed.inject(HttpTestingController);

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;

        elemDefault = new ExerciseHint();
        elemDefault.id = 0;
        elemDefault.title = 'AAAAAAA';
        elemDefault.content = 'AAAAAAA';
        elemDefault.exercise = exercise;

        exerciseHint = new ExerciseHint();
        exerciseHint.title = 'AAAAA';
        exerciseHint.content = 'BBBBB';
        exerciseHint.exercise = exercise;
    });

    describe('Service methods', () => {
        it('should find an element', () => {
            const returnedFromService = Object.assign({}, elemDefault);
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(elemDefault);
        });

        it('should create a ExerciseHint', () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                    title: 'AAAAA',
                    content: 'BBBBBB',
                    exercise: {
                        id: 1,
                    },
                },
                elemDefault,
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .create(exerciseHint)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should update a ExerciseHint', () => {
            const returnedFromService = Object.assign(
                {
                    title: 'BBBBBB',
                    content: 'BBBBBB',
                },
                elemDefault,
            );

            const expected = Object.assign({}, returnedFromService);
            service
                .update(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete a ExerciseHint', () => {
            service.delete(123).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            expect(expectedResult).toBe(true);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
