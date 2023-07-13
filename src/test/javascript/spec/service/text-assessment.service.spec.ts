import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { Result } from 'app/entities/result.model';
import { TextAssessmentEvent } from 'app/entities/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { NewStudentParticipationResolver, StudentParticipationResolver } from 'app/exercises/text/assess/text-submission-assessment.route';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { of } from 'rxjs';
import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';

describe('TextAssessment Service', () => {
    let service: TextAssessmentService;
    let httpMock: HttpTestingController;
    const textSubmission = new TextSubmission();
    let actualResponse: any;

    const mockResponse: any = {
        id: 1,
        submissions: [
            {
                id: 1,
                type: 'MANUAL',
                results: [
                    {
                        id: 6,
                        feedbacks: [
                            {
                                id: 6,
                                reference: '8c8d2463ec548efca05e66423bee537b6357e880',
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
                    },
                ],
            },
        ],
    };

    const result = mockResponse.submissions[0].results[0] as Result;

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
    });

    it('should save an assessment', async () => {
        const participationId = 1;

        service
            .save(participationId, result.id!, result.feedbacks!, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp.body));
        const req = httpMock.expectOne({
            url: `api/participations/${1}/results/${6}/text-assessment`,
            method: 'PUT',
        });
        req.flush(result);

        expect(actualResponse).toEqual(result);
    });

    it('should save example assessment', async () => {
        service
            .saveExampleAssessment(exercise.id!, mockResponse.id, result.feedbacks!, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp.body));

        const req = httpMock.expectOne({
            url: `api/exercises/${exercise.id}/example-submissions/${mockResponse.id}/example-text-assessment`,
            method: 'PUT',
        });
        req.flush(result);

        expect(actualResponse).toEqual(result);
    });

    it('should submit an assessment', async () => {
        const participationId = 1;

        service
            .submit(participationId, result.id!, result.feedbacks!, mockResponse.submissions[0].blocks)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp.body));
        const req = httpMock.expectOne({
            url: `api/participations/${1}/results/${6}/submit-text-assessment`,
            method: 'POST',
        });
        req.flush(result);

        expect(actualResponse).toEqual(result);
    });

    it('should update assessment after complaint', () => {
        const feedbacks = result.feedbacks!;
        const textBlocks = mockResponse.submissions[0].blocks;
        const complaintResponse = new ComplaintResponse();
        const participationId = 1;

        service
            .updateAssessmentAfterComplaint(feedbacks, textBlocks, complaintResponse, mockResponse.submissions[0].id, participationId)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp.body));

        const req = httpMock.expectOne({
            url: `api/participations/${1}/submissions/${mockResponse.submissions[0].id}/text-assessment-after-complaint`,
            method: 'PUT',
        });
        req.flush(result);

        expect(actualResponse).toEqual(result);
    });

    it('should cancel assessment', () => {
        const participationId = 1;
        const submissionId = 1;

        service
            .cancelAssessment(participationId, submissionId)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp));
        const req = httpMock.expectOne({
            url: `api/participations/${participationId}/submissions/${submissionId}/cancel-assessment`,
            method: 'POST',
        });
        req.flush(result);

        expect(actualResponse).toEqual(result);
    });

    it('should delete assessment', () => {
        const participationId = 1;
        const submissionId = 18;

        service
            .deleteAssessment(participationId, submissionId, result.id!)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp));
        const req = httpMock.expectOne({
            url: `api/participations/${1}/text-submissions/${submissionId}/results/${result.id}`,
            method: 'DELETE',
        });
        req.flush(result);

        httpMock.verify();
    });

    it('should delete example assessment', () => {
        const exerciseId = 10;
        const submissionId = 9;

        service
            .deleteExampleAssessment(exerciseId, submissionId)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp));
        const req = httpMock.expectOne({
            url: `api/exercises/${exerciseId}/example-submissions/${submissionId}/example-text-assessment/feedback`,
            method: 'DELETE',
        });
        req.flush(result);

        httpMock.verify();
    });

    it('should send assessment event to analytics', fakeAsync(() => {
        const assessmentEvent: TextAssessmentEvent = new TextAssessmentEvent();
        service.addTextAssessmentEvent(assessmentEvent).subscribe((response) => {
            expect(response.status).toBe(200);
        });
        const mockRequest = httpMock.expectOne({ url: 'api/event-insights/text-assessment/events', method: 'POST' });
        mockRequest.flush(mockResponse);
        tick();
    }));

    it('should get feedback data for submission', fakeAsync(() => {
        const submissionId = 42;
        service
            .getFeedbackDataForExerciseSubmission(submissionId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.submissions?.[0].results?.[0].feedbacks).toEqual(result.feedbacks));

        const req = httpMock.expectOne({
            url: `api/text-submissions/${submissionId}/for-assessment?correction-round=0`,
            method: 'GET',
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should get feedback data with resultId set', fakeAsync(() => {
        const submissionId = 42;
        const resultId = result.id;

        service
            .getFeedbackDataForExerciseSubmission(submissionId, undefined, resultId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.submissions?.[0].results?.[0].feedbacks).toEqual(result.feedbacks));

        const req = httpMock.expectOne({
            url: `api/text-submissions/${submissionId}/for-assessment?resultId=6`,
            method: 'GET',
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should get example result for defined exercise and submission', () => {
        service
            .getExampleResult(exercise.id!, mockResponse.submissions[0].id)
            .pipe(take(1))
            .subscribe((resp) => (actualResponse = resp));
        const req = httpMock.expectOne({
            url: `api/exercises/${exercise.id}/submissions/${mockResponse.submissions[0].id}/example-result`,
            method: 'GET',
        });
        req.flush(mockResponse);
        expect(actualResponse).toEqual(mockResponse);
    });

    it('should get number of tutors involved in assessment', fakeAsync(() => {
        const responseNumberOfTutors = 5;
        service
            .getNumberOfTutorsInvolvedInAssessment(1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toBe(responseNumberOfTutors));

        const req = httpMock.expectOne({
            url: `api/event-insights/text-assessment/courses/1/text-exercises/1/tutors-involved`,
            method: 'GET',
        });
        req.flush(responseNumberOfTutors);
        tick();
    }));

    it('should match blocks with feedbacks', () => {
        const blocks = mockResponse.submissions[0].blocks;
        const feedbacks = result.feedbacks!;

        const expected = [new TextBlockRef(blocks[0], feedbacks[0])];
        const actual = TextAssessmentService.matchBlocksWithFeedbacks(blocks, feedbacks);
        expect(actual).toStrictEqual(expected);
    });

    it('should resolve new StudentParticipations for TextSubmissionAssessmentComponent', () => {
        const resolver = TestBed.inject(NewStudentParticipationResolver);
        const textSubmissionService = TestBed.inject(TextSubmissionService);
        const newStudentParticipationStub = jest.spyOn(textSubmissionService, 'getSubmissionWithoutAssessment').mockReturnValue(of(textSubmission));

        const snapshot = {
            paramMap: convertToParamMap({ exerciseId: 1 }),
            queryParamMap: convertToParamMap({ correctionRound: 0 }),
        } as unknown as ActivatedRouteSnapshot;

        resolver.resolve(snapshot);

        expect(newStudentParticipationStub).toHaveBeenCalledOnce();
        expect(newStudentParticipationStub).toHaveBeenCalledWith(1, 'lock', 0);
    });

    it('should resolve the needed StudentParticipations for TextSubmissionAssessmentComponent', () => {
        const resolver = TestBed.inject(StudentParticipationResolver);
        const studentParticipationSpy = jest.spyOn(service, 'getFeedbackDataForExerciseSubmission');

        const snapshot = {
            paramMap: convertToParamMap({ participationId: 1, submissionId: 2, resultId: 1 }),
            queryParamMap: convertToParamMap({ correctionRound: 0 }),
        } as unknown as ActivatedRouteSnapshot;

        resolver.resolve(snapshot);

        expect(studentParticipationSpy).toHaveBeenCalledOnce();
        expect(studentParticipationSpy).toHaveBeenCalledWith(2, undefined, 1);
    });

    afterEach(() => {
        httpMock.verify();
    });
});
