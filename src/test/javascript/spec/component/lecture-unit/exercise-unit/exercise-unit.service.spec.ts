import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';

describe('ExerciseUnitService', () => {
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
        const returnedFromService = [{ ...elemDefault }];
        service
            .findAllByLectureId(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultArray = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResultArray.body[0]).toEqual(elemDefault);
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
        expect(expectedResult.body).toEqual(expected);
    });
});
