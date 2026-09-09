import { describe, expect, it } from 'vitest';

import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
} from 'app/assessment/shared/entities/feedback.model';

describe('Feedback', () => {
    describe('getDisplayTitle', () => {
        it('should strip AI suggestion prefixes from feedback text', () => {
            expect(Feedback.getDisplayTitle({ text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Model` } as Feedback)).toBe('Model');
            expect(Feedback.getDisplayTitle({ text: `${FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER}Model` } as Feedback)).toBe('Model');
            expect(Feedback.getDisplayTitle({ text: `${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}Model` } as Feedback)).toBe('Model');
        });

        it('should return manual header text for unreferenced feedback', () => {
            expect(Feedback.getDisplayTitle({ text: 'Player', detailText: 'Good work' } as Feedback)).toBe('Player');
        });

        it('should not expose text as title when linked to a grading instruction', () => {
            expect(
                Feedback.getDisplayTitle({
                    text: 'ignored',
                    gradingInstruction: { id: 1 },
                } as Feedback),
            ).toBeUndefined();
        });

        it('should not expose a suggestion title when linked to a grading instruction', () => {
            expect(
                Feedback.getDisplayTitle({
                    text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Model`,
                    gradingInstruction: { id: 1 },
                } as Feedback),
            ).toBeUndefined();
        });
    });

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
});
