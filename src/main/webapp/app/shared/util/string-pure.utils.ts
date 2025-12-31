/**
 * Prepares a string for insertion into a regex.
 * Example: [test].*[/test] -> \[test\].*\[\/test\]
 * @param text
 */
export const escapeStringForUseInRegex = (text: string) => {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};

export type RegExpLineNumberMatchArray = Array<[number, string]>;
/**
 * Executes a regex on a multi line text ("text \n more text") and returns the [lineNumber of the match, matched object] in a tuple.
 * The given regex must have the global flag, otherwise the multiline match will cause an out of memory exception.
 *
 * @param multiLineText in which to search for matches of the regex.
 * @param regex RegExp object.
 * @return the matches found in the multiline string.
 * @throws Error if a regex is provided without a global flag.
 */
export const matchRegexWithLineNumbers = (multiLineText: string, regex: RegExp): RegExpLineNumberMatchArray => {
    if (!regex.flags.includes('g')) {
        throw new Error('Regex must contain global flag, otherwise this function will run out of memory.');
    }
    const result: RegExpLineNumberMatchArray = [];
    let match = regex.exec(multiLineText);
    while (match) {
        const lineNumber = multiLineText.substring(0, match.index + match[1].length + 1).split('\n').length - 1;
        result.push([lineNumber, match[1]]);
        match = regex.exec(multiLineText);
    }
    return result;
};

/**
 * Checks if provided value is undefined. Can be used to filter an array:
 * [].filter(notUndefined)
 *
 * @param anyValue
 * @return boolean
 */
export const notUndefined = (anyValue: any): boolean => anyValue !== undefined;
