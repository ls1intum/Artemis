import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { ExerciseResultService } from 'app/entities/exercise-result/exercise-result.service';
import { IExerciseResult, ExerciseResult } from 'app/shared/model/exercise-result.model';
import { AssessmentType } from 'app/shared/model/enumerations/assessment-type.model';

describe('Service Tests', () => {
    describe('ExerciseResult Service', () => {
        let injector: TestBed;
        let service: ExerciseResultService;
        let httpMock: HttpTestingController;
        let elemDefault: IExerciseResult;
        let expectedResult: IExerciseResult | IExerciseResult[] | boolean | null;
        let currentDate: moment.Moment;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
            });
            expectedResult = null;
            injector = getTestBed();
            service = injector.get(ExerciseResultService);
            httpMock = injector.get(HttpTestingController);
            currentDate = moment();

            elemDefault = new ExerciseResult(0, 'AAAAAAA', currentDate, false, false, 0, false, false, AssessmentType.AUTOMATIC, false, false);
        });

        describe('Service methods', () => {
            it('should find an element', () => {
                const returnedFromService = Object.assign(
                    {
                        completionDate: currentDate.format(DATE_TIME_FORMAT),
                    },
                    elemDefault,
                );

                service.find(123).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(elemDefault);
            });

            it('should create a ExerciseResult', () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                        completionDate: currentDate.format(DATE_TIME_FORMAT),
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        completionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.create(new ExerciseResult()).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(expected);
            });

            it('should update a ExerciseResult', () => {
                const returnedFromService = Object.assign(
                    {
                        resultString: 'BBBBBB',
                        completionDate: currentDate.format(DATE_TIME_FORMAT),
                        successful: true,
                        buildArtifact: true,
                        score: 1,
                        rated: true,
                        hasFeedback: true,
                        assessmentType: 'BBBBBB',
                        hasComplaint: true,
                        exampleResult: true,
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        completionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.update(expected).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(expected);
            });

            it('should return a list of ExerciseResult', () => {
                const returnedFromService = Object.assign(
                    {
                        resultString: 'BBBBBB',
                        completionDate: currentDate.format(DATE_TIME_FORMAT),
                        successful: true,
                        buildArtifact: true,
                        score: 1,
                        rated: true,
                        hasFeedback: true,
                        assessmentType: 'BBBBBB',
                        hasComplaint: true,
                        exampleResult: true,
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        completionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.query().subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush([returnedFromService]);
                httpMock.verify();
                expect(expectedResult).toContainEqual(expected);
            });

            it('should delete a ExerciseResult', () => {
                service.delete(123).subscribe(resp => (expectedResult = resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
                expect(expectedResult);
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
