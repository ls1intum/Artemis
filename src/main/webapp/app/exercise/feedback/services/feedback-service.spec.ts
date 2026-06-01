import { expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

describe('FeedbackService', () => {
    setupTestBed({ zoneless: true });
    let service: FeedbackService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [FeedbackService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(FeedbackService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should filter feedbacks by test ids', () => {
        const includedFeedbacks: Feedback[] = [
            { testCase: { testName: 'task1test1', id: 25 }, detailText: 'first test' },
            { testCase: { testName: 'task1test2', id: 26 }, detailText: 'second test' },
        ];
        const excludedFeedbacks: Feedback[] = [{ testCase: { testName: 'task2', id: 42 }, detailText: 'filtered out' }];
        const feedbacks: Feedback[] = [...includedFeedbacks, ...excludedFeedbacks];

        expect(service.filterFeedback(feedbacks, [25, 26])).toEqual(includedFeedbacks);
    });

    it('should get long feedback text from server', async () => {
        const feedbackId = 42;
        const expectedResponse = 'This is a long feedback text.';
        const promise = service.getLongFeedbackText(feedbackId);

        const req = httpMock.expectOne(`api/assessment/feedbacks/${feedbackId}/long-feedback`);
        expect(req.request.method).toBe('GET');

        req.flush(expectedResponse);
        const feedbackText = await promise;
        expect(feedbackText).toBe(expectedResponse);
    });
});
