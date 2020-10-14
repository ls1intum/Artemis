import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { SERVER_API_URL } from 'app/app.constants';

describe('TextAssessment Service', () => {
    let injector: TestBed;
    let service: TextAssessmentsService;
    let httpMock: HttpTestingController;
    let textSubmission: TextSubmission;
    let mockResponse: any;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(TextAssessmentsService);
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

    describe('Tracking', async () => {
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
    });

    afterEach(() => {
        httpMock.verify();
    });
});
