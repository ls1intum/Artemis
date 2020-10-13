import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { ISubmission, Submission } from 'app/shared/model/submission.model';
import { SubmissionType } from 'app/shared/model/enumerations/submission-type.model';

describe('Service Tests', () => {
    describe('Submission Service', () => {
        let injector: TestBed;
        let service: SubmissionService;
        let httpMock: HttpTestingController;
        let elemDefault: ISubmission;
        let expectedResult: ISubmission | ISubmission[] | boolean | null;
        let currentDate: moment.Moment;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
            });
            expectedResult = null;
            injector = getTestBed();
            service = injector.get(SubmissionService);
            httpMock = injector.get(HttpTestingController);
            currentDate = moment();

            elemDefault = new Submission(0, false, currentDate, SubmissionType.MANUAL, false);
        });

        describe('Service methods', () => {
            it('should find an element', () => {
                const returnedFromService = Object.assign(
                    {
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                    },
                    elemDefault,
                );

                service.find(123).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(elemDefault);
            });

            it('should create a Submission', () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        submissionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.create(new Submission()).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(expected);
            });

            it('should update a Submission', () => {
                const returnedFromService = Object.assign(
                    {
                        submitted: true,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                        type: 'BBBBBB',
                        exampleSubmission: true,
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        submissionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.update(expected).subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(returnedFromService);
                expect(expectedResult).toMatchObject(expected);
            });

            it('should return a list of Submission', () => {
                const returnedFromService = Object.assign(
                    {
                        submitted: true,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                        type: 'BBBBBB',
                        exampleSubmission: true,
                    },
                    elemDefault,
                );

                const expected = Object.assign(
                    {
                        submissionDate: currentDate,
                    },
                    returnedFromService,
                );

                service.query().subscribe(resp => (expectedResult = resp.body));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush([returnedFromService]);
                httpMock.verify();
                expect(expectedResult).toContainEqual(expected);
            });

            it('should delete a Submission', () => {
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
