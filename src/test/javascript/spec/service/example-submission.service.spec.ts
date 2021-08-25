import { getTestBed, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';

const expect = chai.expect;

describe('Example Submission Service', () => {
    let injector: TestBed;
    let httpMock: HttpTestingController;
    let service: ExampleSubmissionService;
    let expectedResult: any;
    let elemDefault: ExampleSubmission;
    let studentSubmission: Submission;

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
        service = injector.get(ExampleSubmissionService);
        httpMock = injector.get(HttpTestingController);

        expectedResult = {} as HttpResponse<ExampleSubmission[]>;
        elemDefault = new ExampleSubmission();
        elemDefault.id = 1;
        elemDefault.usedForTutorial = false;
        elemDefault.exercise = {
            id: 1,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
        } as unknown as Exercise;
        elemDefault.submission = {
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as TextSubmission;
        elemDefault.submission.results = [
            {
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(elemDefault.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        elemDefault.assessmentExplanation = 'exampleSubmissionTest';
        studentSubmission = elemDefault.submission;
    });

    describe('Service methods', () => {
        it('should create an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).to.deep.equal(expected);
        }));

        it('should update an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .update(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).to.deep.equal(expected);
        }));

        it('should delete an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            service.delete(exampleSubmissionId).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
            expect(expectedResult).to.be.true;
        }));

        it('should return an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .get(exampleSubmissionId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).to.deep.equal(expected);
        }));
        it('should import an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .import(studentSubmission, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).to.deep.equal(expected);
        }));
    });
});
