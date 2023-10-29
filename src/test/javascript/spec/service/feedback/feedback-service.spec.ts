import { FeedbackService } from 'app/exercises/shared/feedback/feedback.service';
import { Feedback } from 'app/entities/feedback.model';

describe('FeedbackService', () => {
    let service: FeedbackService;

    beforeEach(() => {
        service = new FeedbackService();
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
});
