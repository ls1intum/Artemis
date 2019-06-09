/**
 * Prepares a string for insertion into a regex.
 * Example: [test].*[/test] -> \[test\].*\[\/test\]
 * @param s
 */
export const escapeStringForUseInRegex = (s: string) => {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};

type StringPositions = Array<{ start: number; end: number; word: string }>;
/**
 * Insert a string that is segmented by a specified delimiter, and the a dictionary that provides the
 * start and end positions of the segments.
 * E.g.: "word1, word2" -> [{start: 0, end: 4, word: "word1"}, {start: 6, end: 10, word: "word2"}]
 * @param stringToSegment string which should be provided segment information about
 * @param delimiter delimiter by which the string is segmented (e.g. ", ")
 * @param result returns a list of StringPositions
 */
export const getStringSegmentPositions = (stringToSegment: string, delimiter: string, result: StringPositions = []): StringPositions => {
    const nextComma = stringToSegment.indexOf(delimiter);
    const lastElement = result.length ? result[result.length - 1] : null;
    // End condition: the string does not have any more segments.
    if (nextComma === -1) {
        return [
            ...result,
            { start: lastElement ? lastElement.end + delimiter.length : 0, end: ((lastElement && lastElement.end) || 0) + stringToSegment.length - 1, word: stringToSegment },
        ];
    }
    const nextWord = stringToSegment.slice(0, nextComma);
    const newResult = [
        ...result,
        {
            start: lastElement ? lastElement.end + delimiter.length : 0,
            end: ((lastElement && lastElement.end + delimiter.length) || 0) + nextComma - delimiter.length,
            word: nextWord,
        },
    ];
    const restString = stringToSegment.slice(nextComma + delimiter.length);
    return getStringSegmentPositions(restString, delimiter, newResult);
};
