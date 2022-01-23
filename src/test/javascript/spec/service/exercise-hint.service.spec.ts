import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextHint } from 'app/entities/hestia/text-hint-model';
import { MockExerciseService } from '../helpers/mocks/service/mock-exercise.service';

describe('ExerciseHint Service', () => {
    let service: ExerciseHintService;
    let httpMock: HttpTestingController;
    let elemDefault: TextHint;
    let exerciseHint: TextHint;
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

        elemDefault = new TextHint();
        elemDefault.id = 0;
        elemDefault.title = 'AAAAAAA';
        elemDefault.content = 'AAAAAAA';
        elemDefault.exercise = exercise;

        exerciseHint = new TextHint();
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
    });

    afterEach(() => {
        httpMock.verify();
    });
});
