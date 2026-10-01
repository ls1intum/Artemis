import { describe, expect, it } from 'vitest';

import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    FeedbackSuggestionType,
    buildFeedbackTextForReview,
} from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

describe('Feedback', () => {
    describe('getReferenceLineRange', () => {
        it('should parse valid programming reference line ranges', () => {
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:3-5' })).toEqual({ start: 3, end: 5 });
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:3' })).toEqual({ start: 3, end: 3 });
        });

        it('should accept the first line of a file, since reference lines are 0-based', () => {
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:0' })).toEqual({ start: 0, end: 0 });
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:0-2' })).toEqual({ start: 0, end: 2 });
        });

        it.each(['file:_line:1', 'file:   _line:1', 'file:src/Main.java_line:1-0', 'file:src/Main.java_line:-1'])(
            'should reject malformed programming reference %s',
            (reference) => {
                expect(Feedback.getReferenceLineRange({ reference })).toBeUndefined();
            },
        );
    });

    describe('getFeedbackSuggestionType', () => {
        it('should return NO_SUGGESTION for plain feedback text', () => {
            expect(Feedback.getFeedbackSuggestionType('Just a comment')).toBe(FeedbackSuggestionType.NO_SUGGESTION);
            expect(Feedback.getFeedbackSuggestionType(undefined)).toBe(FeedbackSuggestionType.NO_SUGGESTION);
            expect(Feedback.getFeedbackSuggestionType({ text: 'Just a comment' })).toBe(FeedbackSuggestionType.NO_SUGGESTION);
        });

        it('should return SUGGESTED for a bare suggestion prefix', () => {
            expect(Feedback.getFeedbackSuggestionType(`${FEEDBACK_SUGGESTION_IDENTIFIER}Missing null check`)).toBe(FeedbackSuggestionType.SUGGESTED);
        });

        it('should return ACCEPTED for an accepted-suggestion prefix', () => {
            expect(Feedback.getFeedbackSuggestionType(`${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check`)).toBe(FeedbackSuggestionType.ACCEPTED);
            expect(Feedback.getFeedbackSuggestionType({ text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Missing null check` })).toBe(FeedbackSuggestionType.ACCEPTED);
        });

        it('should return ADAPTED for an adapted-suggestion prefix, accepting either a string or a Feedback object', () => {
            expect(Feedback.getFeedbackSuggestionType(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check`)).toBe(FeedbackSuggestionType.ADAPTED);
            expect(Feedback.getFeedbackSuggestionType({ text: `${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Missing null check` })).toBe(FeedbackSuggestionType.ADAPTED);
        });
    });

    describe('buildFeedbackTextForReview', () => {
        const gradingInstruction = { feedback: 'Poor' } as GradingInstruction;

        it('should drop an accepted AI suggestion title even with a matched grading instruction, keeping the criterion and detail text', () => {
            const feedback = {
                text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Incorrect city`,
                detailText: 'The answer provided does not name the capital of France.',
                gradingInstruction,
            } as Feedback;

            expect(buildFeedbackTextForReview(feedback)).toBe('Poor<br>The answer provided does not name the capital of France.');
        });

        it('should drop an adapted AI suggestion title without a grading instruction, keeping only the detail text', () => {
            const feedback = {
                text: `${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Incorrect city`,
                detailText: 'The answer provided does not name the capital of France.',
            } as Feedback;

            expect(buildFeedbackTextForReview(feedback)).toBe('The answer provided does not name the capital of France.');
        });

        it('should not repeat the title of a feedback linked to a grading instruction in its body', () => {
            const feedback = { text: 'Missing capital', gradingInstruction } as Feedback;

            expect(buildFeedbackTextForReview(feedback)).toBe('Poor');
        });

        it('should show the text of a feedback without a body of its own as its body, as it was written before titles existed', () => {
            const feedback = { text: 'The capital is Paris.' } as Feedback;

            expect(buildFeedbackTextForReview(feedback)).toBe('The capital is Paris.');
        });

        it('should drop even a non-suggestion text when addFeedbackText is false', () => {
            const feedback = { text: 'File Main.java at line 3' } as Feedback;

            expect(buildFeedbackTextForReview(feedback, false)).toBe('');
        });
    });

    describe('isTextTitle', () => {
        it('should read the text as a title once the feedback has a description or a grading instruction', () => {
            expect(Feedback.isTextTitle({ text: 'Title', detailText: 'Description' } as Feedback)).toBe(true);
            expect(Feedback.isTextTitle({ text: 'Title', gradingInstruction: { feedback: 'Poor' } as GradingInstruction } as Feedback)).toBe(true);
            expect(Feedback.isTextTitle({ text: 'A comment written before titles existed' } as Feedback)).toBe(false);
        });
    });

    describe('getDefaultTitleKey', () => {
        it('should choose the default title by the sign of the points', () => {
            expect(Feedback.getDefaultTitleKey(2)).toBe('artemisApp.feedback.type.positive');
            expect(Feedback.getDefaultTitleKey(-0.5)).toBe('artemisApp.feedback.type.needsRevision');
            expect(Feedback.getDefaultTitleKey(0)).toBe('artemisApp.feedback.type.feedback');
            expect(Feedback.getDefaultTitleKey(undefined)).toBe('artemisApp.feedback.type.feedback');
        });
    });
});
