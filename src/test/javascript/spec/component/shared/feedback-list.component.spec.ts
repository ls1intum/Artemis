import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/detail/result-detail.component';
import { FeedbackListComponent } from 'app/exercises/shared/feedback/list/feedback-list.component';

describe('FeedbackListComponent', () => {
    const comp = new FeedbackListComponent();

    it('should generate correct class names for correct feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Test,
                positive: true,
                category: '',
            },
            {
                type: FeedbackItemType.Feedback,
                category: '',
                positive: true,
            },
            {
                type: FeedbackItemType.Feedback,
                category: '',
                credits: 1,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-success'));
    });

    it('should generate correct class names for warning feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Issue,
                category: '',
            },
            {
                type: FeedbackItemType.Feedback,
                category: '',
                credits: 0,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-warning'));
    });

    it('should generate correct class names for wrong feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Test,
                category: '',
                positive: false,
            },
            {
                type: FeedbackItemType.Feedback,
                category: '',
                positive: false,
            },
            {
                type: FeedbackItemType.Feedback,
                category: '',
                credits: -1,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-danger'));
    });
});
