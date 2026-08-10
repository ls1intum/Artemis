import { describe, expect, it } from 'vitest';

import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    FeedbackSuggestionType,
} from 'app/assessment/shared/entities/feedback.model';

describe('Feedback', () => {
    describe('getReferenceLineRange', () => {
        it('should parse valid programming reference line ranges', () => {
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:3-5' })).toEqual({ start: 3, end: 5 });
            expect(Feedback.getReferenceLineRange({ reference: 'file:src/Main.java_line:3' })).toEqual({ start: 3, end: 3 });
        });

        it.each(['file:_line:1', 'file:   _line:1', 'file:src/Main.java_line:0', 'file:src/Main.java_line:0-2', 'file:src/Main.java_line:1-0'])(
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
});
