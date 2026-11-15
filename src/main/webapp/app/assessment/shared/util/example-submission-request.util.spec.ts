import { describe, expect, it } from 'vitest';
import { toExampleSubmissionRequest } from 'app/assessment/shared/util/example-submission-request.util';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

describe('toExampleSubmissionRequest', () => {
    it('keeps the fields the server reads and drops the links back to the editor entities', () => {
        const feedback = new Feedback();
        feedback.id = 3;
        feedback.detailText = 'detail';
        feedback.credits = 2;
        feedback.type = FeedbackType.MANUAL;
        const result = new Result();
        result.feedbacks = [feedback];
        feedback.result = result;
        const submission = new TextSubmission();
        submission.id = 5;
        submission.text = 'answer';
        submission.results = [result];
        const exampleSubmission: ExampleSubmission = {
            id: 9,
            usedForTutorial: true,
            assessmentExplanation: 'why',
            exercise: { id: 7 } as ExampleSubmission['exercise'],
            submission,
        };

        const request = toExampleSubmissionRequest(exampleSubmission);

        expect(request).toEqual({
            id: 9,
            usedForTutorial: true,
            assessmentExplanation: 'why',
            exercise: { id: 7 },
            submission: {
                id: 5,
                text: 'answer',
                model: undefined,
                explanationText: undefined,
                results: [expect.objectContaining({ feedbacks: [expect.objectContaining({ id: 3, detailText: 'detail', credits: 2, type: 'MANUAL' })] })],
            },
        });
        expect(() => JSON.stringify(request)).not.toThrow();
    });

    it('leaves absent parts absent', () => {
        expect(toExampleSubmissionRequest({ id: 1 })).toEqual({ id: 1, usedForTutorial: undefined, assessmentExplanation: undefined, exercise: undefined, submission: undefined });
    });
});
