import { FeedbackService } from 'app/exercises/shared/feedback/feedback.service';
import { Feedback } from 'app/entities/feedback.model';

describe('FeedbackService', () => {
    let service: FeedbackService;

    beforeEach(() => {
        service = new FeedbackService();
    });

    it('should filter feedbacks by strings', () => {
        const includedFeedbacks = [
            { text: 'task1', name: 'first test' },
            { text: 'task1', name: 'second test' },
        ];
        const excludedFeedbacks = [{ text: 'filtered out' }];
        const feedbacks = [...includedFeedbacks, ...excludedFeedbacks] as Feedback[];

        expect(service.filterFeedback(feedbacks, ['task1'])).toEqual(includedFeedbacks);
    });
});
