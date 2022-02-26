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
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { FeedbackConflictResolver, NewStudentParticipationResolver, StudentParticipationResolver } from 'app/exercises/text/assess/text-submission-assessment.route';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { of } from 'rxjs';
import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';

describe('TextAssessment Service', () => {
    let service: TextAssessmentService;
    let httpMock: HttpTestingController;
    let textSubmission: TextSubmission;
    let mockResponse: any;
    let expectedResult: any;

    const exercise = {
        id: 20,
        type: ExerciseType.TEXT,
        assessmentType: AssessmentType.MANUAL,
        course: { id: 123, isAtLeastInstructor: true } as Course,
    } as TextExercise;

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
                    // submissionDate: '2020-07-07T14:34:25.194518+02:00',
                    durationInMinutes: 0,
                    text: 'Test\n\nTest\n\nTest',
                    results: [
                        {
                            id: 6,
                            resultString: '1 of 1 points',
                            // completionDate: '2020-07-09T16:28:18.138615+02:00',
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
                    blocks: [
                        {
                            id: '8c8d2463ec548efca05e66423bee537b6357e880',
                            text: 'Test',
                            startIndex: 0,
                            endIndex: 4,
                        },
                    ],
                },
            ],
        };
    });

    it('should save an assessment', async () => {
        let result: Result;
        const participationId = 1;
        result = mockResponse.submissions[0].results[0];
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .save(participationId, mockResponse.submissions[0].results[0].id, mockResponse.submissions[0].results[0].feedbacks, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp.body));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${1}/results/${6}/text-assessment`,
            method: 'PUT',
        });
        req.flush(returnedFromService);

        expect(expectedResult).toEqual(result);
    });

    it('should save example assessment', async () => {
        let result: Result;
        result = mockResponse.submissions[0].results[0];
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .saveExampleAssessment(exercise.id!, mockResponse.submissions[0].id, mockResponse.submissions[0].results[0].feedbacks, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp.body));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/exercises/${exercise.id}/example-submissions/${mockResponse.submissions[0].id}/example-text-assessment`,
            method: 'PUT',
        });
        req.flush(returnedFromService);

        expect(expectedResult).toEqual(result);
    });

    it('should submit an assessment', async () => {
        let result: Result;
        const participationId = 1;
        result = mockResponse.submissions[0].results[0];
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .submit(participationId, mockResponse.submissions[0].results[0].id, mockResponse.submissions[0].results[0].feedbacks, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp.body));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${1}/results/${6}/submit-text-assessment`,
            method: 'POST',
        });
        req.flush(returnedFromService);

        expect(expectedResult).toEqual(result);
    });

    it('should update assessment after complaint', () => {
        const feedbacks = mockResponse.submissions[0].results[0].feedbacks;
        const textBlocks = mockResponse.submissions[0].blocks;
        const complaintResponse = new ComplaintResponse();
        const participationId = 1;
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .updateAssessmentAfterComplaint(feedbacks, textBlocks, complaintResponse, mockResponse.submissions[0].id, participationId)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp.body));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${1}/submissions/${mockResponse.submissions[0].id}/text-assessment-after-complaint`,
            method: 'PUT',
        });
        req.flush(returnedFromService);

        expect(expectedResult).toEqual(mockResponse.submissions[0].results[0]);
    });

    it('should cancel assessment', () => {
        const participationId = 1;
        const submissionId = 1;
        const result = mockResponse.submissions[0].results[0];
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .cancelAssessment(participationId, submissionId)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${participationId}/submissions/${submissionId}/cancel-assessment`,
            method: 'POST',
        });
        req.flush(returnedFromService);

        expect(expectedResult).toEqual(result);
    });

    it('should delete assessment', () => {
        const participationId = 1;
        const submissionId = 18;
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .deleteAssessment(participationId, submissionId, mockResponse.submissions[0].results[0].id)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${1}/text-submissions/${submissionId}/results/${mockResponse.submissions[0].results[0].id}`,
            method: 'DELETE',
        });
        req.flush(returnedFromService);

        httpMock.verify();
    });

    it('should delete example assessment', () => {
        const exerciseId = 10;
        const submissionId = 9;
        const returnedFromService = Object.assign({}, mockResponse.submissions[0].results[0]);

        service
            .deleteExampleAssessment(exerciseId, submissionId)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/exercises/${exerciseId}/example-submissions/${submissionId}/example-text-assessment/feedback`,
            method: 'DELETE',
        });
        req.flush(returnedFromService);

        httpMock.verify();
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

    it('should get feedback data with resultId set', fakeAsync(() => {
        const submissionId = 42;
        const returnedFromService = Object.assign({}, mockResponse);
        const participationId = 42;
        const resultId = mockResponse.submissions[0].results[0].id;

        service
            .getFeedbackDataForExerciseSubmission(participationId, submissionId, undefined, resultId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.submissions?.[0].results?.[0].feedbacks).toEqual(mockResponse.submissions[0].results[0].feedbacks));

        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/participations/${participationId}/submissions/${submissionId}/for-text-assessment?resultId=6`,
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

    it('should get example result for defined exercise and submission', () => {
        const returnedFromService = Object.assign({}, mockResponse);
        service
            .getExampleResult(exercise.id!, mockResponse.submissions[0].id)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({
            url: `${SERVER_API_URL}api/exercises/${exercise.id}/submissions/${mockResponse.submissions[0].id}/example-result`,
            method: 'GET',
        });
        req.flush(returnedFromService);
        expect(expectedResult).toEqual(mockResponse);
    });

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

    it('should match blocks with feedbacks', () => {
        const blocks = mockResponse.submissions[0].blocks;
        const feedbacks = mockResponse.submissions[0].results[0].feedbacks;

        const expected = [new TextBlockRef(blocks[0], feedbacks[0])];
        const actual = TextAssessmentService.matchBlocksWithFeedbacks(blocks, feedbacks);
        expect(actual).toStrictEqual(expected);
    });

    it('should resolve new StudentParticipations for TextSubmissionAssessmentComponent', () => {
        let resolver: NewStudentParticipationResolver;
        let textSubmissionService: TextSubmissionService;
        let newStudentParticipationStub: jest.SpyInstance;

        resolver = TestBed.inject(NewStudentParticipationResolver);
        textSubmissionService = TestBed.inject(TextSubmissionService);
        newStudentParticipationStub = jest.spyOn(textSubmissionService, 'getTextSubmissionForExerciseForCorrectionRoundWithoutAssessment').mockReturnValue(of(textSubmission));

        const snapshot = {
            paramMap: convertToParamMap({ exerciseId: 1 }),
            queryParamMap: convertToParamMap({ correctionRound: 0 }),
        } as unknown as ActivatedRouteSnapshot;

        resolver.resolve(snapshot);

        expect(newStudentParticipationStub).toHaveBeenCalledWith(1, 'lock', 0);
    });

    it('should resolve the needed StudentParticipations for TextSubmissionAssessmentComponent', () => {
        let resolver: StudentParticipationResolver;
        let studentParticipationSpy: jest.SpyInstance;
        resolver = TestBed.inject(StudentParticipationResolver);
        studentParticipationSpy = jest.spyOn(service, 'getFeedbackDataForExerciseSubmission');

        const snapshot = {
            paramMap: convertToParamMap({ participationId: 1, submissionId: 2, resultId: 1 }),
            queryParamMap: convertToParamMap({ correctionRound: 0 }),
        } as unknown as ActivatedRouteSnapshot;

        resolver.resolve(snapshot);

        expect(studentParticipationSpy).toHaveBeenCalledWith(1, 2, undefined, 1);
    });

    it('should resolve the needed textSubmissions for TextFeedbackConflictsComponent', () => {
        let resolver: FeedbackConflictResolver;
        let feedbackConflictSpy: jest.SpyInstance;
        resolver = TestBed.inject(FeedbackConflictResolver);
        feedbackConflictSpy = jest.spyOn(service, 'getConflictingTextSubmissions');

        const snapshot = {
            paramMap: convertToParamMap({ participationId: 1, submissionId: 2, feedbackId: 3 }),
            queryParamMap: convertToParamMap({ correctionRound: 0 }),
        } as unknown as ActivatedRouteSnapshot;

        resolver.resolve(snapshot);

        expect(feedbackConflictSpy).toHaveBeenCalledWith(1, 2, 3);
    });

    afterEach(() => {
        httpMock.verify();
    });
});
