import { describe, expect, it } from 'vitest';
import { matchesRegexFully } from 'app/foundation/util/regex.util';

describe('matchesRegexFully', () => {
    it('should return true if regex is undefined', () => {
        expect(matchesRegexFully('test', undefined)).toBe(true);
    });

    it('should return false if input is undefined', () => {
        expect(matchesRegexFully(undefined, 'test')).toBe(false);
    });

    it('should return true for a full match', () => {
        expect(matchesRegexFully('test', 'test')).toBe(true);
    });

    it('should return false for a partial match', () => {
        expect(matchesRegexFully('testing', 'test')).toBe(false);
    });

    it('should return true for a match with regex special characters', () => {
        expect(matchesRegexFully('test123', 'test\\d+')).toBe(true);
    });

    it('should return false for no match', () => {
        expect(matchesRegexFully('test', 'no-match')).toBe(false);
    });

    it('should handle regex without ^ and $', () => {
        expect(matchesRegexFully('test', 'test')).toBe(true);
        expect(matchesRegexFully('test', '^test')).toBe(true);
        expect(matchesRegexFully('test', 'test$')).toBe(true);
        expect(matchesRegexFully('test', '^test$')).toBe(true);
    });
});
