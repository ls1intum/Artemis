import { describe, expect, it } from 'vitest';

import { Feedback } from 'app/assessment/shared/entities/feedback.model';

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
});
