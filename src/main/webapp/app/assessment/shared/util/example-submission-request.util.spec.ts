import { describe, expect, it } from 'vitest';
import { toExampleSubmission, toExampleSubmissionRequest } from 'app/assessment/shared/util/example-submission-request.util';
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

describe('toExampleSubmission', () => {
    it('maps a text submission with its blocks and language', () => {
        const mapped = toExampleSubmission({
            id: 1,
            usedForTutorial: false,
            submission: {
                id: 5,
                submissionExerciseType: 'text',
                text: 'abcdef',
                language: 'GERMAN',
                blocks: [{ id: 'b', text: 'abc', startIndex: 0, endIndex: 3, type: 'MANUAL' }],
            },
        });

        const submission = mapped.submission as TextSubmission;
        expect(submission).toBeInstanceOf(TextSubmission);
        expect(submission.language).toBe('GERMAN');
        expect(submission.blocks).toHaveLength(1);
        expect(submission.blocks![0]).toMatchObject({ id: 'b', text: 'abc', startIndex: 0, endIndex: 3, type: 'MANUAL' });
    });

    it('keeps absent blocks absent and maps a modeling submission', () => {
        const text = toExampleSubmission({ id: 1, submission: { id: 5, submissionExerciseType: 'text', text: 'a' } }).submission as TextSubmission;
        expect(text.blocks).toBeUndefined();

        const modeling = toExampleSubmission({ id: 2, submission: { id: 6, submissionExerciseType: 'modeling', model: '{}', explanationText: 'why' } }).submission;
        expect(modeling).toMatchObject({ id: 6, model: '{}', explanationText: 'why' });
        expect(toExampleSubmission({ id: 3 }).submission).toBeUndefined();
    });
});
