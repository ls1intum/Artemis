import { matchRegexWithLineNumbers } from 'app/shared/util/string-pure.utils';

describe('StringPureUtils', () => {
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
