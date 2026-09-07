import { describe, expect, it } from 'vitest';
import { parseCorrectionRound } from 'app/assessment/shared/util/correction-round.util';

describe('correction round util', () => {
    it.each([
        { raw: '0', expected: 0 },
        { raw: '1', expected: 1 },
        { raw: ' 1 ', expected: 1 },
        { raw: '2', expected: 2 },
    ])('should read $raw as correction round $expected', ({ raw, expected }) => {
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
        { raw: 'NaN', description: 'literally NaN' },
        { raw: '2147483648', description: 'one above the int the server binds the round to' },
        { raw: '9007199254740993', description: 'beyond the safe integer range' },
        { raw: '9'.repeat(400), description: 'so long that Number() returns Infinity' },
        { raw: '1e3', description: 'exponential' },
        { raw: '0x2', description: 'hexadecimal' },
        { raw: '+1', description: 'explicitly signed' },
        { raw: '1 2', description: 'two numbers' },
        { raw: '2px', description: 'number with a suffix' },
    ])('should fall back to the first round for a $description value', ({ raw }) => {
        // These are the values a hand-edited or truncated URL produces. Bare Number() would pass NaN, Infinity, a
        // fraction or a negative round on to the endpoint and to the results array as an index.
        expect(parseCorrectionRound(raw)).toBe(0);
    });

    it('should accept a round that is a plain integer string of more than one digit', () => {
        // Guards against a stricter check that only accepts single digits, since the number of rounds is not capped at 10.
        expect(parseCorrectionRound('12')).toBe(12);
    });

    it('should accept the largest round the server can bind', () => {
        // Pins the bound from the other side, so that rejecting oversized values cannot turn into an off-by-one that
        // also rejects a value the endpoint would have taken.
        expect(parseCorrectionRound('2147483647')).toBe(2147483647);
    });
});
