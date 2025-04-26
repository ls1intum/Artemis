import { getStringSegmentPositions, matchRegexWithLineNumbers } from 'app/shared/util/global.utils';

describe('GlobalUtils', () => {
    describe('getStringSegmentPositions', () => {
        it('should return correct segments of provided string and single character delimiter', () => {
            const testString = 'word1,word2,word3';
            const delimiter = ',';
            const expectedResult = [
                { start: 0, end: 4, word: 'word1' },
                { start: 6, end: 10, word: 'word2' },
                { start: 12, end: 16, word: 'word3' },
            ];

            const segments = getStringSegmentPositions(testString, delimiter);
            expect(segments).toEqual(expectedResult);
        });

        it('should return correct segments of provided string and multiple character delimiter', () => {
            const testString = 'word1 - word2 - word3';
            const delimiter = ' - ';
            const expectedResult = [
                { start: 0, end: 4, word: 'word1' },
                { start: 8, end: 12, word: 'word2' },
                { start: 16, end: 20, word: 'word3' },
            ];

            const segments = getStringSegmentPositions(testString, delimiter);
            expect(segments).toEqual(expectedResult);
        });

        it('should return the string as a single segment if it does not contain the delimiter', () => {
            const testString = 'word1word2word3';
            const delimiter = 'x';
            const expectedResult = [{ start: 0, end: 14, word: 'word1word2word3' }];

            const segments = getStringSegmentPositions(testString, delimiter);
            expect(segments).toEqual(expectedResult);
        });

        it('should return a single segment for the empty string', () => {
            const testString = '';
            const delimiter = 'x';
            const expectedResult = [{ start: 0, end: 0, word: '' }];

            const segments = getStringSegmentPositions(testString, delimiter);
            expect(segments).toEqual(expectedResult);
        });
    });

    describe('matchRegexWithLineNumbers', () => {
        const globalRegex = /.*(def).*/g;
        const nonGlobalRegex = /.*(def).*/;
        const multilineText = 'abc \n def \n ghi \n def \n def \n jkl';
        const nonMultilineText = 'abc def abc def';

        it('should match the line numbers correctly in the given multiline string', () => {
            const expectedMatches = [
                [1, 'def'],
                [3, 'def'],
                [4, 'def'],
            ];
            const matches = matchRegexWithLineNumbers(multilineText, globalRegex);

            expect(matches).toEqual(expectedMatches);
        });

        it('should return matches if the string is not multiline', () => {
            const expectedMatches = [[0, 'def']];
            const matches = matchRegexWithLineNumbers(nonMultilineText, globalRegex);

            expect(matches).toEqual(expectedMatches);
        });

        it('should throw an error if a string without the global flag is provided', () => {
            expect(() => matchRegexWithLineNumbers(multilineText, nonGlobalRegex)).toThrow('Regex must contain global flag, otherwise this function will run out of memory.');
        });
    });
});
