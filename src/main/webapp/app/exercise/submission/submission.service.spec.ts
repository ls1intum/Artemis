import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercise/submission/submission.service';
import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Submission, SubmissionExerciseType, SubmissionType, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import dayjs from 'dayjs/esm';
import { SubmissionResponseDTO } from 'app/exercise/shared/entities/submission/submission-response.dto';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('Submission Service', () => {
    let service: SubmissionService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    let submission: TextSubmission;
    let submissionResponseDTO: SubmissionResponseDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
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
        submissionResponseDTO = {
            id: submission.id!,
            submitted: submission.submitted!,
            type: submission.type,
            text: submission.text,
            submissionExerciseType: SubmissionExerciseType.TEXT,
            results: submission.results!.map((result) => ({
                id: result.id!,
                score: result.score,
                rated: result.rated!,
                hasComplaint: result.hasComplaint,
                feedbacks: result.feedbacks,
            })),
        };
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should delete an existing submission', () => {
        service.delete(187).subscribe((resp) => (expectedResult = resp.ok));
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        expect(expectedResult).toBe(true);
    });

    it('should find all submissions of a given participation', () => {
        const participationId = 1;
        service
            .findAllSubmissionsOfParticipation(participationId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toHaveLength(1);
                expect(resp.body![0]).toBeInstanceOf(TextSubmission);
                expect(resp.body![0].id).toBe(submission.id);
                expect(resp.body![0].results![0].submission).toBe(resp.body![0]);
            });
        const req = httpMock.expectOne({ url: `api/exercise/participations/${participationId}/submissions`, method: 'GET' });
        req.flush([submissionResponseDTO]);
    });

    it('should get test run submission for a given exercise', () => {
        const exerciseId = 1;

        service
            .getTestRunSubmissionsForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toHaveLength(1);
                expect(resp.body![0]).toBeInstanceOf(TextSubmission);
                expect(resp.body![0].latestResult).toBe(resp.body![0].results![0]);
            });
        const req = httpMock.expectOne({ url: `api/exercise/exercises/${exerciseId}/test-run-submissions`, method: 'GET' });
        req.flush([submissionResponseDTO]);
    });

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

    it('should get submissions with complaints for tutor', () => {
        const exerciseId = 1;
        const submissionDateStr = '2022-02-02T12:34:56.789Z';
        const complaintSubmittedTimeStr = '2022-02-03T22:11:33.444Z';
        const returnedSubmission = deepClone(submissionResponseDTO);
        returnedSubmission.submissionDate = submissionDateStr;

        const returnedFromService = [
            {
                submission: returnedSubmission,
                complaint: { id: 2, submittedTime: complaintSubmittedTimeStr, complaintIsAccepted: true },
            },
        ];

        service
            .getSubmissionsWithComplaintsForTutor(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toHaveLength(1);
                const submissionWithComplaint = resp.body![0];
                expect(submissionWithComplaint.submission.submissionDate).toEqual(dayjs(submissionDateStr));
                expect(submissionWithComplaint.complaint.submittedTime).toEqual(dayjs(complaintSubmittedTimeStr));
                expect(submissionWithComplaint.complaint.accepted).toBe(true);
            });
        const req = httpMock.expectOne({ url: `api/exercise/exercises/${exerciseId}/submissions-with-complaints`, method: 'GET' });
        req.flush(returnedFromService);
    });
});
