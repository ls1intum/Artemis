import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { getLatestSubmissionResult } from 'app/entities/submission.model';

describe('TextAssessment Service', () => {
    let injector: TestBed;
    let service: TextAssessmentService;
    let httpMock: HttpTestingController;
    let textSubmission: TextSubmission;
    let mockResponse: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(TextAssessmentService);
        httpMock = injector.get(HttpTestingController);

        textSubmission = new TextSubmission();

        mockResponse = {
            type: 'student',
            id: 1,
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
            submissions: [
                {
                    submissionExerciseType: 'text',
                    id: 1,
                    submitted: true,
                    type: 'MANUAL',
                    submissionDate: '2020-07-07T14:34:25.194518+02:00',
                    durationInMinutes: 0,
                    text: 'Test\n\nTest\n\nTest',
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

    it('should not parse jwt from header', async () => {
        service.getFeedbackDataForExerciseSubmission(1).subscribe((studentParticipation) => {
            expect((studentParticipation.submissions![0] as TextSubmission).atheneTextAssessmentTrackingToken).toBeUndefined();
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });
        mockRequest.flush(mockResponse);
    });

    it('should parse jwt from header', async () => {
        service.getFeedbackDataForExerciseSubmission(1).subscribe((studentParticipation) => {
            expect((studentParticipation.submissions![0] as TextSubmission).atheneTextAssessmentTrackingToken).toEqual('12345');
        });

        const mockRequest = httpMock.expectOne({ method: 'GET' });

        mockRequest.flush(mockResponse, { headers: { 'x-athene-tracking-authorization': '12345' } });
    });

    it('should get feedback data for submission', async () => {
        const submissionId = 42;
        const returnedFromService = Object.assign({}, mockResponse);
        service
            .getFeedbackDataForExerciseSubmission(submissionId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: mockResponse }));

        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/text-assessments/submission/${submissionId}?correction-round=0`, method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should get conflicting text submissions', async () => {
        const submissionId = 42;
        const feedbackId = 42;
        const submission = ({
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as TextSubmission;
        submission.results = [
            ({
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
            } as unknown) as Result,
        ];
        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const returnedFromService = Object.assign({}, [submission]);
        service
            .getConflictingTextSubmissions(submissionId, feedbackId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: [submission] }));

        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/text-assessments/submission/${submissionId}/feedback/${feedbackId}/feedback-conflicts`, method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should solve feedback conflicts', async () => {
        const exerciseId = 1;
        const feedbackConflict = ({
            id: 1,
            conflict: false,
            type: 'INCONSISTENT_COMMENT',
            firstFeedback: new Feedback(),
            secondFeedback: new Feedback(),
        } as unknown) as FeedbackConflict;
        const returnedFromService = Object.assign({}, feedbackConflict);
        service
            .solveFeedbackConflict(exerciseId, feedbackConflict.id!)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: feedbackConflict }));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/text-assessments/exercise/${exerciseId}/feedbackConflict/${feedbackConflict.id}/solve-feedback-conflict`,
            method: 'GET',
        });
        req.flush(JSON.stringify(returnedFromService));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
