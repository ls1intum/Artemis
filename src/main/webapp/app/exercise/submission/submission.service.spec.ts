import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SubmissionService } from 'app/exercise/submission/submission.service';
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
import { Submission, SubmissionType, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

describe('Submission Service', () => {
    let service: SubmissionService;
    let httpMock: HttpTestingController;
    let expectedResult: any;
    let submission: TextSubmission;

    it.each(
        [ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.PROGRAMMING, ExerciseType.FILE_UPLOAD].flatMap((type) => [false, true].map((instructor) => ({ type, instructor }))),
    )('loads own exam assessments and one proposed submission for $type, instructor=$instructor', ({ type, instructor }) => {
        const prefix = `api/${type.replace('-', '')}/exercises/7/${type}`;
        const first = { id: 1, participation: { id: 11 }, results: [{ id: 21, correctionRound: 1 }] };
        service.getExamAssessmentQueue(type, 7, 1, true, instructor).subscribe((submissions) => expect(submissions.map((submission) => submission.id)).toEqual([1, 2]));
        const tutorRoute = type !== ExerciseType.PROGRAMMING && !instructor;
        const own = httpMock.expectOne((request) => request.url === `${prefix}-submissions${tutorRoute ? '/assessed-by-me' : ''}`);
        expect(own.request.params.get('correction-round')).toBe('1');
        expect(own.request.params.has('assessedByTutor')).toBe(type === ExerciseType.PROGRAMMING);
        expect(own.request.params.has('assessedByMe')).toBe(type !== ExerciseType.PROGRAMMING && instructor);
        own.flush([first]);
        const offered = httpMock.expectOne((request) => request.url === `${prefix}-submission-without-assessment`);
        expect(offered.request.params.get('correction-round')).toBe('1');
        expect(offered.request.params.has('head')).toBe(type === ExerciseType.TEXT);
        offered.flush({ id: 2, participation: { id: 12 }, results: [] });
    });

    it('keeps own assessments when the lock limit prevents proposing another', () => {
        service.getExamAssessmentQueue(ExerciseType.TEXT, 7, 0, true).subscribe((submissions) => expect(submissions.map((submission) => submission.id)).toEqual([1]));
        httpMock.expectOne((request) => request.url.endsWith('/text-submissions/assessed-by-me')).flush([{ id: 1, participation: { id: 11 } }]);
        httpMock
            .expectOne((request) => request.url.endsWith('/text-submission-without-assessment'))
            .flush({ errorKey: 'lockedSubmissionsLimitReached' }, { status: 400, statusText: 'Bad Request' });
    });

    it('does not request manual assessment queues for quizzes', () => {
        service.getExamAssessmentQueue(ExerciseType.QUIZ, 7, 0, true).subscribe((submissions) => expect(submissions).toEqual([]));
        httpMock.expectNone((request) => request.url.includes('/quiz'));
    });

    it('does not request a proposed assessment while the correction round is disabled', () => {
        service.getExamAssessmentQueue(ExerciseType.PROGRAMMING, 7, 1, false).subscribe((submissions) => expect(submissions).toEqual([]));
        httpMock.expectOne((request) => request.url.endsWith('/programming-submissions')).flush([]);
        httpMock.expectNone((request) => request.url.endsWith('/programming-submission-without-assessment'));
    });

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
    });

    it('uses the explicit instructor complaint queue URL', () => {
        service.getSubmissionsWithComplaintsForInstructor(42).subscribe((response) => expect(response.body).toEqual([]));
        const req = httpMock.expectOne({ url: 'api/exercise/exercises/42/submissions-with-complaints/for-instructor', method: 'GET' });
        req.flush([]);
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
        const returnedFromService = [...[submission]];
        const expected = [...[submission]];
        service
            .findAllSubmissionsOfParticipation(participationId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(expected));
        const req = httpMock.expectOne({ url: `api/exercise/participations/${participationId}/submissions`, method: 'GET' });
        req.flush(returnedFromService);
    });

    it('should get test run submission for a given exercise', () => {
        const exerciseId = 1;

        const returnedFromService = [submission];
        let converted: Submission | undefined;
        service
            .getTestRunSubmissionsForExercise(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => (converted = resp.body![0]));
        const req = httpMock.expectOne({ url: `api/exercise/exercises/${exerciseId}/test-run-submissions`, method: 'GET' });
        req.flush(returnedFromService);

        // convertSubmissionFromServer returns a detached copy, so the response no longer shares its results with the
        // fixture. Assert the converted values and that latestResult points at the copy's own last result.
        expect(converted!.id).toBe(submission.id);
        expect(converted!.results).toHaveLength(1);
        expect(converted!.results![0].id).toBe(getLatestSubmissionResult(submission)!.id);
        expect(converted!.latestResult).toBe(getLatestSubmissionResult(converted!));
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

        const firstRoundResult: Result = {
            id: 3556,
            score: 24,
            rated: true,
            hasComplaint: false,
            correctionRound: 0,
            feedbacks: [firstFeedback],
        };

        const secondRoundResult = submission.results![0];
        secondRoundResult.correctionRound = 1;
        // Prepended on purpose: the round a result belongs to is what decides the comparison, not its position.
        submission.results!.unshift(firstRoundResult);

        expect(secondFeedback.copiedFeedbackId).toBeUndefined();

        const secondRoundFeedbacks = secondRoundResult.feedbacks!;
        secondRoundFeedbacks.push(secondFeedback);

        // Copy checking should not be done for correction round 0.
        service.handleFeedbackCorrectionRoundTag(0, submission);
        expect(secondFeedback.copiedFeedbackId).toBeUndefined();

        // Only the second feedback has identical values to the first one, the other feedback should remain untouched.
        service.handleFeedbackCorrectionRoundTag(1, submission);
        expect(secondRoundFeedbacks[0].copiedFeedbackId).toBeUndefined();
        expect(secondFeedback.copiedFeedbackId).toBe(firstFeedback.id);

        secondFeedback.text = 'Feedback changed';
        // Feedback.text is changed so the Feedback is not a direct copy anymore.
        service.handleFeedbackCorrectionRoundTag(1, submission);
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

        const complaint: ComplaintDTO = {
            submittedTime: complaintSubmittedTimeStr as any, // String should be converted to proper type by the tested service.
            complaintIsAccepted: false,
            result: { id: 2374, successful: true },
        };
        submission.results![0].testCaseCount = 10;
        submission.results![0].passedTestCaseCount = 7;
        submission.results![0].codeIssueCount = 3;
        const returnedFromService = [
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
                // a rejected complaint must stay rejected: `complaintIsAccepted: false` maps to `accepted: false`, not to undefined
                expect(submissionWithComplaint.complaint.accepted).toBe(false);
                // the reduced result of the complaint is what the more feedback request table renders: it needs the
                // programming numbers and the submission of the listed result with the same id
                expect(submissionWithComplaint.complaint.result!.testCaseCount).toBe(10);
                expect(submissionWithComplaint.complaint.result!.passedTestCaseCount).toBe(7);
                expect(submissionWithComplaint.complaint.result!.codeIssueCount).toBe(3);
                expect(submissionWithComplaint.complaint.result!.submission).toBe(submissionWithComplaint.submission);
                // an Athena result is stripped from the listed submission, so `successful` has to survive from the complaint itself
                expect(submissionWithComplaint.complaint.result!.successful).toBe(true);
            });
        const req = httpMock.expectOne({ url: `api/exercise/exercises/${exerciseId}/submissions-with-complaints`, method: 'GET' });
        req.flush(returnedFromService);
    });
});
