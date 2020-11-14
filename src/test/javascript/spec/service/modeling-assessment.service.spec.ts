import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Exercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { of } from 'rxjs/internal/observable/of';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { SERVER_API_URL } from 'app/app.constants';

const expect = chai.expect;

describe('Modeling Assessment Service', () => {
    let injector: TestBed;
    let httpMock: HttpTestingController;
    let service: ModelingAssessmentService;
    let expectedResult: any;
    let httpExpectedResult: any;
    let elemDefault: Result;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(ModelingAssessmentService);
        httpMock = injector.get(HttpTestingController);

        expectedResult = {} as Result;
        httpExpectedResult = {} as HttpResponse<Result>;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        describe('methods returning a result', () => {
            elemDefault = new Result();
            elemDefault.id = 1;
            elemDefault.resultString = 'result';
            elemDefault.score = 5;
            elemDefault.hasComplaint = false;
            it('should save an assessment', async () => {
                const submissionId = 187;
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .saveAssessment(feedbacks, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/assessment`,
                    method: 'PUT',
                });
                req.flush(returnedFromService);
                expect(expectedResult).to.deep.equal(elemDefault);
            });

            it('should save an example assessment', async () => {
                const exampleSubmissionId = 187;
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .saveExampleAssessment(feedbacks, exampleSubmissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `${SERVER_API_URL}api/modeling-submissions/${exampleSubmissionId}/example-assessment`,
                    method: 'PUT',
                });
                req.flush(returnedFromService);
                expect(expectedResult).to.deep.equal(elemDefault);
            });

            it('should get an assessment', async () => {
                const submissionId = 187;
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .getAssessment(submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/result`,
                    method: 'GET',
                });
                req.flush(returnedFromService);
                expect(expectedResult).to.deep.equal(elemDefault);
            });

            it('should get an example assessment', async () => {
                const submissionId = 187;
                const exerciseId = 188;
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .getExampleAssessment(exerciseId, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (expectedResult = resp));
                const req = httpMock.expectOne({
                    url: `${SERVER_API_URL}api/exercise/${exerciseId}/modeling-submissions/${submissionId}/example-assessment`,
                    method: 'GET',
                });
                req.flush(returnedFromService);
                expect(expectedResult).to.deep.equal(elemDefault);
            });

            it('should update assessment after complaint', async () => {
                const feedbacks = [
                    {
                        id: 0,
                        credits: 3,
                        reference: 'reference',
                    } as Feedback,
                    {
                        id: 1,
                        credits: 1,
                    } as Feedback,
                ];
                let complaintResponse = new ComplaintResponse();
                complaintResponse.id = 1;
                complaintResponse.responseText = 'That is true';
                const submissionId = 1;
                const returnedFromService = { ...elemDefault };
                const expected = { ...returnedFromService };
                service
                    .updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId)
                    .pipe(take(1))
                    .subscribe((resp) => (httpExpectedResult = resp));
                const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/modeling-submissions/${submissionId}/assessment-after-complaint`, method: 'PUT' });
                req.flush(returnedFromService);
                expect(httpExpectedResult.body).to.deep.equal(expected);
            });
        });

        it('should get optimal submissions', async () => {
            const elem = 1;
            const exerciseId = 187;
            const returnedFromService = Object.assign([], [elem]);
            service
                .getOptimalSubmissions(exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${SERVER_API_URL}api/exercises/${exerciseId}/optimal-model-submissions`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult).to.deep.equal([elem]);
        });
    });
});
