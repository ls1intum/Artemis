import { describe, expect, it } from 'vitest';
import { toFeedback, toFeedbacksOfResult } from 'app/assessment/shared/util/feedback-response.util';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

describe('feedback response util', () => {
    it('maps the enums, the test case and the grading instruction', () => {
        const feedback = toFeedback({
            id: 4,
            text: 'text',
            credits: 2,
            positive: true,
            type: 'AUTOMATIC',
            testCase: { id: 8, testName: 'testA', visibility: 'AFTER_DUE_DATE', type: 'BEHAVIORAL' },
            gradingInstruction: { id: 3, credits: 1, usageCount: 2 },
        });

        expect(feedback.type).toBe(FeedbackType.AUTOMATIC);
        expect(feedback.testCase).toMatchObject({ id: 8, testName: 'testA', visibility: Visibility.AfterDueDate, type: 'BEHAVIORAL' });
        expect(feedback.gradingInstruction).toMatchObject({ id: 3, credits: 1, usageCount: 2 });
    });

    it('keeps absent fields absent and links the feedback to its result', () => {
        const result = new Result();

        const [feedback] = toFeedbacksOfResult([{ id: 1 }], result);

        expect(feedback.credits).toBeUndefined();
        expect(feedback.type).toBeUndefined();
        expect(feedback.testCase).toBeUndefined();
        expect(feedback.gradingInstruction).toBeUndefined();
        expect(feedback.result).toBe(result);
    });
});
