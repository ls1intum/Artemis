import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockProvider } from 'ng-mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextHint } from 'app/entities/hestia/text-hint-model';

describe('ExerciseHint Service', () => {
    let injector: TestBed;
    let service: ExerciseHintService;
    let httpMock: HttpTestingController;
    let elemDefault: TextHint;
    let exerciseHint: TextHint;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(ExerciseService, {
                    convertDateFromClient<E extends Exercise>(res: E): E {
                        return res;
                    },
                }),
            ],
        });
        expectedResult = {} as HttpResponse<ExerciseHint>;
        injector = getTestBed();
        service = injector.get(ExerciseHintService);
        httpMock = injector.get(HttpTestingController);

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
