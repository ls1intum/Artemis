import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';

describe('FeedbackAnalysisService', () => {
    let service: FeedbackAnalysisService;
    let httpMock: HttpTestingController;

    const feedbackDetailsMock: FeedbackDetail[] = [
        { detailText: 'Feedback 1', testCaseName: 'test1', count: 5, relativeCount: 25.0, taskNumber: 1 },
        { detailText: 'Feedback 2', testCaseName: 'test2', count: 3, relativeCount: 15.0, taskNumber: 2 },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [FeedbackAnalysisService],
        });

        service = TestBed.inject(FeedbackAnalysisService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('getFeedbackDetailsForExercise', () => {
        it('should retrieve feedback details for a given exercise', async () => {
            const responsePromise = service.getFeedbackDetailsForExercise(1);

            const req = httpMock.expectOne('api/exercises/1/feedback-details');
            expect(req.request.method).toBe('GET');
            req.flush(feedbackDetailsMock);

            const result = await responsePromise;
            expect(result).toEqual(feedbackDetailsMock);
        });
    });
});
