import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { getTestBed, TestBed } from '@angular/core/testing';
import { expect } from '../helpers/jest.fix';
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
import { SERVER_API_URL } from 'app/app.constants';

describe('Submission Service', () => {
    let injector: TestBed;
    let service: SubmissionService;
    let httpMock: HttpTestingController;

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
    });

    it('should delete an existing submission', async () => {
        service.delete(187).subscribe((resp) => expect(resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    it('should find all submissions of a given participation', async () => {
        const participationId = 187;
        const submission = ({
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as TextSubmission;
        submission.result = ({
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown) as Result;
        submission.result.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = Object.assign({}, [submission]);
        service
            .findAllSubmissionsOfParticipation(187)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [submission] }));
        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/participations/${participationId}/submissions`, method: 'GET' });
        req.flush(JSON.stringify([returnedFromService]));
    });

    it('should get test run submission for a given exercise', async () => {
        const exerciseId = 187;
        const submission = ({
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as TextSubmission;
        submission.result = ({
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown) as Result;
        submission.result.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = Object.assign({}, [submission]);
        service
            .getTestRunSubmissionsForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [submission] }));
        const req = httpMock.expectOne({ url: `api/exercises/${exerciseId}/test-run-submissions`, method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
