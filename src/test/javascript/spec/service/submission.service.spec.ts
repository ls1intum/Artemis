import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercises/shared/submission/submission.service';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { Submission, SubmissionType, getLatestSubmissionResult } from 'app/entities/submission.model';
import dayjs from 'dayjs/esm';
import { Complaint } from 'app/entities/complaint.model';

describe('Submission Service', () => {
    let service: SubmissionService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    let submission: TextSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        service = TestBed.inject(SubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
        expectedResult = {} as HttpResponse<Submission[]>;

        submission = {
            id: 1,
            submitted: true,
            type: SubmissionType.TEST,
            text: 'Test\n\nTest\n\nTest',
            results: [
                {
                    id: 2374,
                    score: 8,
                    rated: true,
                    hasFeedback: true,
                    hasComplaint: false,
                },
            ],
        };

        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            },
        ];
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should delete an existing submission', fakeAsync(() => {
        service.delete(187).subscribe((resp) => (expectedResult = resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(expectedResult).toBeTrue();
    }));

    it('should find all submissions of a given participation', fakeAsync(() => {
        const participationId = 1;
        const returnedFromService = [...[submission]];
        const expected = [...[submission]];
        service
            .findAllSubmissionsOfParticipation(participationId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/participations/${participationId}/submissions`, method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get test run submission for a given exercise', fakeAsync(() => {
        const exerciseId = 1;

        const returnedFromService = [submission];
        const expected = [
            {
                ...submission,
                latestResult: getLatestSubmissionResult(submission),
            },
        ];
        service
            .getTestRunSubmissionsForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ url: `api/exercises/${exerciseId}/test-run-submissions`, method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should handle feedback correction round tag', () => {
        const firstFeedback: Feedback = {
            id: 3,
            detailText: 'Feedback',
            credits: 4,
            type: FeedbackType.MANUAL,
        };

        const secondFeedback: Feedback = {
            id: 4,
            detailText: 'Feedback',
            credits: 4,
            type: FeedbackType.MANUAL,
        };

        const firstResult: Result = {
            id: 3556,
            score: 24,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
            feedbacks: [firstFeedback],
        };

        submission.results?.unshift(firstResult);

        expect(secondFeedback.copiedFeedbackId).toBeUndefined();

        const latestResultFeedbacks = getLatestSubmissionResult(submission)!.feedbacks!;
        latestResultFeedbacks?.push(secondFeedback);

        // Copy checking should not be done for correction round 0.
        service.handleFeedbackCorrectionRoundTag(0, submission);
        expect(secondFeedback.copiedFeedbackId).toBeUndefined();

        // Only the second feedback has identical values to the first one, the other feedback should remain untouched.
        service.handleFeedbackCorrectionRoundTag(1, submission);
        expect(latestResultFeedbacks[0].copiedFeedbackId).toBeUndefined();
        expect(secondFeedback.copiedFeedbackId).toBe(firstFeedback.id);

        secondFeedback.text = 'Feedback changed';
        // Feedback.text is changed so the Feedback is not a direct copy anymore.
        service.handleFeedbackCorrectionRoundTag(2, submission);
        expect(secondFeedback.copiedFeedbackId).toBeUndefined();
    });

    it('should convert results date from server', () => {
        const dateStr = '2022-02-02T17:37:53.283Z';
        const date = dayjs(dateStr);

        const result = submission.results![0];
        result.completionDate = dateStr as any; // String should be converted to proper type by the tested service.

        service.convertResultArrayDatesFromServer(submission.results);

        expect(result.completionDate).toEqual(date);
    });

    it('should get submissions with complaints for tutor', fakeAsync(() => {
        const exerciseId = 1;
        const submissionDateStr = '2022-02-02T12:34:56.789Z';
        const complaintSubmittedTimeStr = '2022-02-03T22:11:33.444Z';

        const complaint: Complaint = {
            submittedTime: complaintSubmittedTimeStr as any, // String should be converted to proper type by the tested service.
        };
        const returnedFromService: SubmissionWithComplaintDTO[] = [
            {
                submission,
                complaint,
            },
        ];
        submission.submissionDate = submissionDateStr as any; // String should be converted to proper type by the tested service.

        service
            .getSubmissionsWithComplaintsForTutor(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toHaveLength(1);
                const submissionWithComplaint = resp.body![0];
                expect(submissionWithComplaint.submission.submissionDate).toEqual(dayjs(submissionDateStr));
                expect(submissionWithComplaint.complaint.submittedTime).toEqual(dayjs(complaintSubmittedTimeStr));
            });
        const req = httpMock.expectOne({ url: `api/exercises/${exerciseId}/submissions-with-complaints`, method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));
});
