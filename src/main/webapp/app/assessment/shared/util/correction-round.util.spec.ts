import { describe, expect, it } from 'vitest';
import { correctionRoundToLoad, parseCorrectionRound } from 'app/assessment/shared/util/correction-round.util';

describe('correction round util', () => {
    describe('parseCorrectionRound', () => {
        it.each([
            { raw: '0', expected: 0 },
            { raw: '1', expected: 1 },
            { raw: ' 1 ', expected: 1 },
        ])('should accept $raw as a usable correction round', ({ raw, expected }) => {
            expect(parseCorrectionRound(raw)).toBe(expected);
        });

        it.each([
            { raw: null, description: 'absent' },
            { raw: undefined, description: 'undefined' },
            { raw: '', description: 'empty' },
            { raw: '   ', description: 'whitespace only' },
            { raw: 'abc', description: 'not a number' },
            { raw: '1.5', description: 'fractional' },
            { raw: '-1', description: 'negative' },
            { raw: 'Infinity', description: 'infinite' },
            { raw: '9007199254740993', description: 'beyond the safe integer range' },
        ])('should reject a $description value', ({ raw }) => {
            // Number() would turn null, '' and whitespace into 0, which is indistinguishable from correction-round=0.
            expect(parseCorrectionRound(raw)).toBeUndefined();
        });
    });

    describe('correctionRoundToLoad', () => {
        it('should keep a usable round', () => {
            expect(correctionRoundToLoad('1')).toBe(1);
        });

        it.each([null, undefined, '', '   ', 'abc', '1.5', '-1', 'Infinity'])('should fall back to the first round for %s', (raw) => {
            // Callers that request data need a concrete round, and an unusable value must never reach the server.
            expect(correctionRoundToLoad(raw)).toBe(0);
        });
    });
});
