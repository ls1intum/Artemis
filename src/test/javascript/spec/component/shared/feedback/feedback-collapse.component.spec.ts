import { FeedbackCollapseComponent } from 'app/exercises/shared/feedback/collapse/feedback-collapse.component';

describe('FeedbackCollapseComponent', () => {
    /*
     * Same value as in feedback-collapse.component.ts
     */
    const FEEDBACK_PREVIEW_CHARACTER_LIMIT = 300;
    let comp: FeedbackCollapseComponent;

    beforeEach(() => {
        comp = new FeedbackCollapseComponent();
    });

    it('should not truncate if not necessary', () => {
        comp.text = 'a'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT - 1);
        comp.ngOnInit();

        expect(comp.previewText).toBeUndefined();
    });

    it('should truncate if necessary', () => {
        const text = '0123456789'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        comp.text = text;
        comp.ngOnInit();

        const expected = text.slice(0, FEEDBACK_PREVIEW_CHARACTER_LIMIT);

        expect(comp.previewText).toBe(expected);
    });

    it('should only show first line if truncated', () => {
        const text = '0123456789\n'.repeat(FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        comp.text = text;
        comp.ngOnInit();

        const expected = text.slice(0, text.indexOf('\n'));

        expect(comp.previewText).toBe(expected);
    });

    it('should only show the first line of feedback if truncating necessary', () => {
        comp.text = 'Multi\nLine\nText' + 'a'.repeat(300);
        comp.ngOnInit();

        expect(comp.previewText).toBe('Multi');
    });
});
