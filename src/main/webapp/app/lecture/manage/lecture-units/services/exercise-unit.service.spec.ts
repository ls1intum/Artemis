import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ExerciseUnitService } from 'app/lecture/manage/lecture-units/services/exercise-unit.service';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

describe('ExerciseUnitService', () => {
    let service: ExerciseUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseUnit;
    let expectedResult: any;
    let expectedResultArray: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureUnitService, {
                    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        return res;
                    },
                    convertLectureUnitResponseArrayDatesFromServer<T extends LectureUnit>(res: HttpResponse<T[]>): HttpResponse<T[]> {
                        return res;
                    },
                }),
                MockProvider(ExerciseService),
            ],
        });
        expectedResult = {} as HttpResponse<ExerciseUnit>;
        expectedResultArray = {} as HttpResponse<ExerciseUnit[]>;
        service = TestBed.inject(ExerciseUnitService);
        httpMock = TestBed.inject(HttpTestingController);

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
        const returnedFromService = [Object.assign({}, elemDefault)];
        service
            .findAllByLectureId(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultArray = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResultArray.body[0]).toEqual(elemDefault);
    });

    it('should create an ExerciseUnit', async () => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(new ExerciseUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    });
});
