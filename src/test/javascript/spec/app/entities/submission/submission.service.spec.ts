/* tslint:disable max-line-length */
import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { take, map } from 'rxjs/operators';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { ISubmission, Submission, SubmissionType } from 'app/shared/model/submission.model';

describe('Service Tests', () => {
    describe('Submission Service', () => {
        let injector: TestBed;
        let service: SubmissionService;
        let httpMock: HttpTestingController;
        let elemDefault: ISubmission;
        let currentDate: moment.Moment;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule]
            });
            injector = getTestBed();
            service = injector.get(SubmissionService);
            httpMock = injector.get(HttpTestingController);
            currentDate = moment();

            elemDefault = new Submission(0, false, currentDate, SubmissionType.MANUAL);
        });

        describe('Service methods', async () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign(
                    {
                        submissionDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: elemDefault }));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should create a Submission', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        submissionDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .create(new Submission(null))
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a Submission', async () => {
                const returnedFromService = Object.assign(
                    {
                        submitted: true,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                        type: 'BBBBBB'
                    },
                    elemDefault
                );

                const expected = Object.assign(
                    {
                        submissionDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should return a list of Submission', async () => {
                const returnedFromService = Object.assign(
                    {
                        submitted: true,
                        submissionDate: currentDate.format(DATE_TIME_FORMAT),
                        type: 'BBBBBB'
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        submissionDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .query(expected)
                    .pipe(
                        take(1),
                        map(resp => resp.body)
                    )
                    .subscribe(body => expect(body).toContainEqual(expected));
                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify([returnedFromService]));
                httpMock.verify();
            });

            it('should delete a Submission', async () => {
                const rxPromise = service.delete(123).subscribe(resp => expect(resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
