import { HttpErrorResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';

/**
 * Prepares a string for insertion into a regex.
 * Example: [test].*[/test] -> \[test\].*\[\/test\]
 * @param text
 */
export const escapeStringForUseInRegex = (text: string) => {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};

type StringPositions = Array<{ start: number; end: number; word: string }>;
/**
 * Insert a string that is segmented by a specified delimiter, and a dictionary that provides the
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
    const lastElement = result.last();
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
 * Use alert service to show the error message from the error response
 * @param alertService the service used to show the exception messages to the user
 * @param error the error response that's status is used to determine the error message
 */
export const onError = (alertService: AlertService, error: HttpErrorResponse) => {
    switch (error.status) {
        case 400:
            alertService.error('error.http.400');
            break;
        case 403:
            alertService.error('error.http.403');
            break;
        case 404:
            alertService.error('error.http.404');
            break;
        case 405:
            alertService.error('error.http.405');
            break;
        case 500:
            // Removed to avoid alerts about internal errors as the user can't do anything about it
            // and the alert does not provide any other value
            break;
        default:
            alertService.addAlert({
                type: AlertType.DANGER,
                message: error.message,
                disableTranslation: true,
            });
            break;
    }
};

/**
 * Checks if provided value is undefined. Can be used to filter an array:
 * [].filter(notUndefined)
 *
 * @param anyValue
 * @return boolean
 */
export const notUndefined = (anyValue: any): boolean => anyValue !== undefined;
