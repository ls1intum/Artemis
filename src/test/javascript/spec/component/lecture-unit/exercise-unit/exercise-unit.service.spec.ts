import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseUnitService', () => {
    let injector: TestBed;
    let service: ExerciseUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseUnit;
    let expectedResult: any;
    let expectedResultArray: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertDateFromServerResponse<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        return res;
                    },
                    convertDateArrayFromServerResponse<T extends LectureUnit>(res: HttpResponse<T[]>): HttpResponse<T[]> {
                        return res;
                    },
                }),
                MockProvider(ExerciseService),
            ],
        });
        expectedResult = {} as HttpResponse<ExerciseUnit>;
        expectedResultArray = {} as HttpResponse<ExerciseUnit[]>;
        injector = getTestBed();
        service = injector.get(ExerciseUnitService);
        httpMock = injector.get(HttpTestingController);

        const course = new Course();
        const exercise = new TextExercise(course, undefined);
        exercise.id = 0;
        exercise.shortName = 'example exercise';

        elemDefault = new ExerciseUnit();
        elemDefault.id = 0;
        elemDefault.exercise = exercise;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a list of ExerciseUnit', async () => {
        const returnedFromService = [{ ...elemDefault }];
        service
            .findAllByLectureId(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultArray = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResultArray.body[0]).to.deep.equal(elemDefault);
    });

    it('should create an ExerciseUnit', async () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new ExerciseUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).to.deep.equal(expected);
    });
});
