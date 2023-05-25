import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/collapse/feedback-collapse.component';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';

describe('FeedbackCollapseComponent', () => {
    /*
     * Same value as in feedback-collapse.component.ts
     */
    const FEEDBACK_PREVIEW_CHARACTER_LIMIT = 300;
    let component: FeedbackCollapseComponent;

    beforeEach(() => {
        component = new FeedbackCollapseComponent();
    });

    it('should not truncate if not necessary', () => {
        component.feedback = getFeedbackItem('a'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT - 1));
        component.ngOnInit();

        expect(component.previewText).toBeUndefined();
    });

    it('should truncate if necessary', () => {
        const text = '0123456789'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        component.feedback = getFeedbackItem(text);
        component.ngOnInit();

        const expected = text.slice(0, FEEDBACK_PREVIEW_CHARACTER_LIMIT);

        expect(component.previewText).toBe(expected);
    });

    it('should only show first line if truncated', () => {
        const text = '0123456789\n'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        component.feedback = getFeedbackItem(text);
        component.ngOnInit();

        const expected = text.slice(0, text.indexOf('\n'));

        expect(component.previewText).toBe(expected);
    });

    it('should only show the first line of feedback if truncating necessary', () => {
        component.feedback = getFeedbackItem('Multi\nLine\nText' + 'a'.repeat(300));
        component.ngOnInit();

        expect(component.previewText).toBe('Multi');
    });

    it('should always set the preview text if the feedback has long feedback', () => {
        component.feedback = getFeedbackItem('Truncated text [...]', true);
        component.ngOnInit();

        expect(component.previewText).toBe('Truncated text [...]');
    });

    const getFeedbackItem = (text: string, hasLongFeedbackText = false): FeedbackItem => {
        return {
            credits: undefined,
            name: 'ignored',
            type: 'Test',
            text,
            feedbackReference: {
                id: 1,
                result: { id: 2 },
                hasLongFeedbackText,
            },
        };
    };
});
