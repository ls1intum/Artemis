import { omit } from 'lodash-es';
import { captureException } from '@sentry/browser';
import { Result } from 'app/entities/result.model';
import { Alert } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';

// Cartesian product helper function
const cartesianConcatHelper = (a: any[], b: any[]): any[][] => ([] as any[][]).concat(...a.map((a2) => b.map((b2) => ([] as any[]).concat(a2, b2))));

/**
 * Returns the cartesian product for all arrays provided to the function.
 * Type of the arrays does not matter, it will just return the combinations without any type information.
 * Implementation taken from here: https://gist.github.com/ssippe/1f92625532eef28be6974f898efb23ef.
 * @param a an array
 * @param b another array
 * @param c rest of arrays
 */
export const cartesianProduct = (a: any[], b: any[], ...c: any[][]): any[] => {
    if (!b || b.length === 0) {
        return a;
    }
    const [b2, ...c2] = c;
    const fab = cartesianConcatHelper(a, b);
    return cartesianProduct(fab, b2, ...c2);
};

/**
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Errors/Cyclic_object_value
 * Stringify a circular JSON structure by omitting keys that would close a circle
 *
 * @param val The object you want to stringify
 */
export const stringifyCircular = (val: any): string => {
    const seen = new WeakSet();
    return JSON.stringify(val, (key, value) => {
        if (typeof value === 'object' && value !== null) {
            if (seen.has(value)) {
                return;
            }
            seen.add(value);
        }
        return value;
    });
};

/**
 * Stringifies an object ignoring certain top-level fields
 *
 * @param val Object to stringify
 * @param ignoredFields Fields to omit from object before converting to string
 */
export const stringifyIgnoringFields = (val: any, ...ignoredFields: string[]): string => {
    return JSON.stringify(omit(val, ignoredFields));
};

/**
 * Helper function to make actually rounding possible
 * @param value
 * @param exp
 */
export const round = (value: any, exp?: number) => {
    if (exp == undefined || +exp === 0) {
        return Math.round(value);
    }

    value = +value;
    exp = +exp;

    if (isNaN(value) || !(exp % 1 === 0)) {
        return NaN;
    }

    // Shift
    value = value.toString().split('e');
    value = Math.round(+(value[0] + 'e' + (value[1] ? +value[1] + exp : exp)));

    // Shift back
    value = value.toString().split('e');
    return +(value[0] + 'e' + (value[1] ? +value[1] - exp : -exp));
};

/**
 * Rounds the score to the specified amount in the course object.
 * @param relativeScore The score of the student in the value range [0;1]
 * @param course The course in which the score is displayed. The attribute accuracyOfScores determines the accuracy
 * @returns The rounded percent of the score in the range [0;100]
 */
export const roundScorePercentSpecifiedByCourseSettings = (relativeScore: any, course?: Course) => {
    if (!course) {
        captureException(new Error('The course object used for determining the rounding of scores was undefined'));
    }
    return round(relativeScore * 100, course ? course!.accuracyOfScores : 1);
};

/**
 * Rounds the points to the specified amount in the course object
 * @param points The points of the student
 * @param course The course in which the score is displayed. The attribute accuracyOfScores determines the accuracy
 * @returns The rounded points of the student
 */
export const roundScoreSpecifiedByCourseSettings = (points: any, course?: Course) => {
    if (!course) {
        captureException(new Error('The course object used for determining the rounding of scores was undefined'));
    }
    return round(points, course ? course!.accuracyOfScores : 1);
};

/**
 * finds the latest result based on the max id
 * @param results
 */
export const findLatestResult = (results?: Result[]) => {
    return results && results.length > 0 ? results.reduce((current, result) => (current.id! > result.id! ? current : result)) : undefined;
};

/**
 * This is a workaround to avoid translation not found issues.
 * Checks if the alert message could not be translated and removes the translation-not-found annotation.
 * Sending an alert to Sentry with the missing translation key.
 * @param alert which was sent to the alertService
 */
export const checkForMissingTranslationKey = (alert: Alert) => {
    if (alert?.message?.startsWith('translation-not-found')) {
        // In case a translation key is not found, remove the 'translation-not-found[...]' annotation
        const alertMessageMatch = alert.message.match(/translation-not-found\[(.*?)\]$/);
        if (alertMessageMatch && alertMessageMatch.length > 1) {
            alert.message = alertMessageMatch[1];
        } else {
            // Fallback, in case the bracket is missing
            alert.message = alert.message.replace('translation-not-found', '');
        }
        // Sent a sentry warning with the translation key
        captureException(new Error('Unknown translation key: ' + alert.message));
    }
};
/**
 * Splits a camel case string into individual words and combines them to a new string separated by spaces
 */
export const splitCamelCase = (word: string) => {
    const output = [];
    const regex = /[A-Z]/;
    for (let i = 0; i < word.length; i += 1) {
        if (i === 0) {
            output.push(word[i].toUpperCase());
        } else {
            if (i > 0 && regex.test(word[i])) {
                output.push(' ');
            }
            output.push(word[i]);
        }
    }
    return output.join('');
};

export const isDate = (input: any) => {
    return input instanceof Date || Object.prototype.toString.call(input) === '[object Date]';
};
