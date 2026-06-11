import { describe, expect, it } from 'vitest';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';

describe('Feedback', () => {
    it('should detect feedback that can be shown in the programming editor', () => {
        expect(Feedback.canBeShownInProgrammingEditor({ reference: 'file:src/Main.java_line:4' } as Feedback)).toBe(true);
        expect(
            Feedback.canBeShownInProgrammingEditor({
                text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
                detailText: '{"filePath":"src/Main.java","startLine":4}',
                type: FeedbackType.AUTOMATIC,
            } as Feedback),
        ).toBe(true);
        expect(Feedback.canBeShownInProgrammingEditor({ reference: 'file:src/Main.java' } as Feedback)).toBe(false);
        expect(Feedback.canBeShownInProgrammingEditor({ reference: 'file:src/Main.java_line:invalid' } as Feedback)).toBe(false);
        expect(Feedback.canBeShownInProgrammingEditor({ type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'General feedback' } as Feedback)).toBe(false);
    });
});
