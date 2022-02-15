import { TestBed, tick, fakeAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { TextAssessmentEvent } from 'app/entities/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';

describe('TextAssessment Service', () => {
    let service: TextAssessmentService;
    let httpMock: HttpTestingController;
    let textSubmission: TextSubmission;
    let mockResponse: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(TextAssessmentService);
        httpMock = TestBed.inject(HttpTestingController);

        textSubmission = new TextSubmission();

        mockResponse = {
            type: 'student',
            id: 1,
            submissions: [
                {
                    submissionExerciseType: 'text',
                    id: 1,
                    submitted: true,
                    type: 'MANUAL',
                    submissionDate: '2020-07-07T14:34:25.194518+02:00',
                    durationInMinutes: 0,
                    text: 'Test\n\nTest\n\nTest',
                    results: [
                        {
                            id: 6,
                            resultString: '1 of 1 points',
                            completionDate: '2020-07-09T16:28:18.138615+02:00',
                            successful: true,
                            score: 100,
                            rated: true,
                            hasFeedback: false,
                            feedbacks: [
                                {
                                    id: 6,
                                    detailText: 'Test',
                                    reference: '8c8d2463ec548efca05e66423bee537b6357e880',
                                    credits: 1.0,
                                    positive: true,
                                    type: 'MANUAL',
                                },
                            ],
                            assessmentType: 'MANUAL',
                        },
                    ],
                },
            ],
        };
    });

    it('should not send feedback', async () => {
        service.trackAssessment();
        httpMock.expectNone({ method: 'POST' });
    });

    it('should not send feedback', async () => {
        service.trackAssessment();
        httpMock.expectNone({ method: 'POST' });
    });

    it('should send feedback', async () => {
        textSubmission.atheneTextAssessmentTrackingToken = '12345';
        service.trackAssessment(textSubmission);
        httpMock.expectOne({ url: `${SERVER_API_URL}/athene-tracking/text-exercise-assessment`, method: 'POST' });
    });

    it('should send assessment event to analytics', fakeAsync(() => {
        const assessmentEvent: TextAssessmentEvent = new TextAssessmentEvent();
        service.addTextAssessmentEvent(assessmentEvent).subscribe((response) => {
            expect(response.status).toBe(200);
        });
        const mockRequest = httpMock.expectOne({ url: `${SERVER_API_URL}/api/analytics/text-assessment/events`, method: 'POST' });
        mockRequest.flush(mockResponse);
        tick();
    }));

    it('should not parse jwt from header', fakeAsync(() => {
        service.getFeedbackDataForExerciseSubmission(1, 1).subscribe((studentParticipation) => {
            expect((studentParticipation.submissions![0] as TextSubmission).atheneTextAssessmentTrackingToken).toBeUndefined();
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });
        mockRequest.flush(mockResponse);
        tick();
    }));

    it('should parse jwt from header', fakeAsync(() => {
        service.getFeedbackDataForExerciseSubmission(1, 1).subscribe((studentParticipation) => {
            expect((studentParticipation.submissions![0] as TextSubmission).atheneTextAssessmentTrackingToken).toEqual('12345');
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });
        mockRequest.flush(mockResponse, { headers: { 'x-athene-tracking-authorization': '12345' } });
        tick();
    }));

    it('should get feedback data for submission', fakeAsync(() => {
        const submissionId = 42;
        const returnedFromService = Object.assign({}, mockResponse);
        const participationId = 42;
        service
            .getFeedbackDataForExerciseSubmission(participationId, submissionId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.submissions?.[0].results?.[0].feedbacks).toEqual(mockResponse.submissions[0].results[0].feedbacks));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${participationId}/submissions/${submissionId}/for-text-assessment?correction-round=0`,
            method: 'GET',
        });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get conflicting text submissions', fakeAsync(() => {
        const submissionId = 42;
        const feedbackId = 42;
        const participationId = 42;
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
        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = [...[submission]];
        service
            .getConflictingTextSubmissions(participationId, submissionId, feedbackId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual([submission]));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${participationId}/submissions/${submissionId}/feedback/${feedbackId}/feedback-conflicts`,
            method: 'GET',
        });
        req.flush(returnedFromService);
        tick();
    }));

    it('should solve feedback conflicts', fakeAsync(() => {
        const exerciseId = 1;
        const feedbackConflict = {
            id: 1,
            conflict: false,
            type: 'INCONSISTENT_COMMENT',
            firstFeedback: new Feedback(),
            secondFeedback: new Feedback(),
        } as unknown as FeedbackConflict;
        const returnedFromService = Object.assign({}, feedbackConflict);
        service
            .solveFeedbackConflict(exerciseId, feedbackConflict.id!)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(feedbackConflict));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/exercises/${exerciseId}/feedback-conflicts/${feedbackConflict.id}/solve`,
            method: 'POST',
        });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get number of tutors involved in assessment', fakeAsync(() => {
        const responseNumberOfTutors = 5;
        service
            .getNumberOfTutorsInvolvedInAssessment(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(responseNumberOfTutors));

        const req = httpMock.expectOne({
            url: `/api/analytics/text-assessment/courses/1/text-exercises/1/tutors-involved`,
            method: 'GET',
        });
        req.flush(responseNumberOfTutors);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
