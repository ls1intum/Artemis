import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { getTestBed, TestBed, tick, fakeAsync } from '@angular/core/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';
const expect = chai.expect;

describe('Submission Service', () => {
    let injector: TestBed;
    let service: SubmissionService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    const submission = {
        id: 1,
        submitted: true,
        type: 'AUTOMATIC',
        text: 'Test\n\nTest\n\nTest',
    } as unknown as TextSubmission;
    submission.results = [
        {
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown as Result,
    ];

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
        service = injector.get(SubmissionService);
        httpMock = injector.get(HttpTestingController);
        expectedResult = {} as HttpResponse<Submission[]>;
    });

    it('should delete an existing submission', async () => {
        service.delete(187).subscribe((resp) => (expectedResult = resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        expect(expectedResult).to.be.true;
    });

    it('should find all submissions of a given participation', fakeAsync(() => {
        const participationId = 1;
        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = [...[submission]];
        const expected = [...[submission]];
        service
            .findAllSubmissionsOfParticipation(participationId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/participations/${participationId}/submissions`, method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get test run submission for a given exercise', fakeAsync(() => {
        const exerciseId = 1;
        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = [...[submission]];
        const expected = [...[submission]];
        service
            .getTestRunSubmissionsForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
        const req = httpMock.expectOne({ url: `api/exercises/${exerciseId}/test-run-submissions`, method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
