import { FeedbackListComponent } from 'app/exercises/shared/feedback/list/feedback-list.component';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/feedback/item/feedback-item';

describe('FeedbackListComponent', () => {
    const comp = new FeedbackListComponent();

    it('should generate correct class names for correct feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Test,
                positive: true,
                name: '',
            },
            {
                type: FeedbackItemType.Feedback,
                name: '',
                positive: true,
            },
            {
                type: FeedbackItemType.Feedback,
                name: '',
                credits: 1,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-success'));
    });

    it('should generate correct class names for warning feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Issue,
                name: '',
            },
            {
                type: FeedbackItemType.Feedback,
                name: '',
                credits: 0,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-warning'));
    });

    it('should generate correct class names for wrong feedback items', () => {
        const items: FeedbackItem[] = [
            {
                type: FeedbackItemType.Test,
                name: '',
                positive: false,
            },
            {
                type: FeedbackItemType.Feedback,
                name: '',
                positive: false,
            },
            {
                type: FeedbackItemType.Feedback,
                name: '',
                credits: -1,
            },
        ];

        items.forEach((item) => expect(comp.getClassNameForFeedbackItem(item)).toBe('alert-danger'));
    });
});
