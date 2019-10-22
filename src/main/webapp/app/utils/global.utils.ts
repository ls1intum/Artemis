import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';

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
    if (stringToSegment === '') {
        return [...result, { start: 0, end: 0, word: '' }];
    }
    const nextComma = stringToSegment.indexOf(delimiter);
    const lastElement = result.length ? result[result.length - 1] : null;
    // End condition: the string does not have any more segments.
    if (nextComma === -1) {
        return [
            ...result,
            {
                start: lastElement ? lastElement.end + delimiter.length + 1 : 0,
                end: ((lastElement && lastElement.end + delimiter.length + 1) || 0) + stringToSegment.length - 1,
                word: stringToSegment,
            },
        ];
    }
    const nextWord = stringToSegment.slice(0, nextComma);
    const newResult = [
        ...result,
        {
            start: lastElement ? lastElement.end + delimiter.length + 1 : 0,
            end: ((lastElement && lastElement.end + delimiter.length + 1) || 0) + nextComma - 1,
            word: nextWord,
        },
    ];
    const restString = stringToSegment.slice(nextComma + delimiter.length);
    return getStringSegmentPositions(restString, delimiter, newResult);
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
 * Builds url with an array of parameters passed to it
 * @param url original url
 * @param params array of parameters that will be added to original url
 */
export const buildUrlWithParams = (url: string, params: string[]): string => {
    if (!params || params.length < 1) {
        return url;
    }
    let urlWithParams = `${url}?${params}`;
    if (params.length > 1) {
        for (let i = 1; i < params.length; ++i) {
            urlWithParams += `&${params}`;
        }
    }
    return urlWithParams;
};

/**
 * Use alert service to show the error message from the error response
 * @param jhiAlertService the service used to show the exception messages to the user
 * @param error returned from the request
 */
export const onError = (jhiAlertService: JhiAlertService, error: HttpErrorResponse) => {
    jhiAlertService.error(error.message);
};
