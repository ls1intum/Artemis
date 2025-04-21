import { matchesRegexFully } from 'app/shared/util/regex.util';

describe('matchesRegexFully', () => {
    it('should return true if regex is undefined', () => {
        expect(matchesRegexFully('test', undefined)).toBeTrue();
    });

    it('should return false if input is undefined', () => {
        expect(matchesRegexFully(undefined, 'test')).toBeFalse();
    });

    it('should return true for a full match', () => {
        expect(matchesRegexFully('test', 'test')).toBeTrue();
    });

    it('should return false for a partial match', () => {
        expect(matchesRegexFully('testing', 'test')).toBeFalse();
    });

    it('should return true for a match with regex special characters', () => {
        expect(matchesRegexFully('test123', 'test\\d+')).toBeTrue();
    });

    it('should return false for no match', () => {
        expect(matchesRegexFully('test', 'no-match')).toBeFalse();
    });

    it('should handle regex without ^ and $', () => {
        expect(matchesRegexFully('test', 'test')).toBeTrue();
        expect(matchesRegexFully('test', '^test')).toBeTrue();
        expect(matchesRegexFully('test', 'test$')).toBeTrue();
        expect(matchesRegexFully('test', '^test$')).toBeTrue();
    });
});
