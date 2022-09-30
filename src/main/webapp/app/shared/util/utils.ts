import { omit, sum } from 'lodash-es';
import { captureException } from '@sentry/browser';
import { Result } from 'app/entities/result.model';
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
    return round(relativeScore * 100, course?.accuracyOfScores ?? 1);
};

/**
 * Rounds the given value to the accuracy defined by the course.
 * @param value The value that should be rounded.
 * @param course The course which defines the accuracy to which the value should be rounded.
 * @returns The rounded value.
 */
export const roundValueSpecifiedByCourseSettings = (value: any, course?: Course) => {
    if (!course) {
        captureException(new Error('The course object used for determining the rounding of scores was undefined'));
    }
    return round(value, course?.accuracyOfScores ?? 1);
};

/**
 * Computes the average value for the given array.
 * @param values The array for which the average should be computed.
 * @returns The average value of the array. Zero for an empty array.
 */
export const average = (values: Array<number>): number => {
    if (values.length === 0) {
        return 0;
    } else {
        return sum(values) / values.length;
    }
};

/**
 * finds the latest result based on the max id
 * @param results
 */
export const findLatestResult = (results?: Result[]) => {
    return results && results.length > 0 ? results.reduce((current, result) => (current.id! > result.id! ? current : result)) : undefined;
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

/**
 * Represents the inclusive range with a lower and upper bound
 */
export class Range {
    constructor(public lowerBound: number, public upperBound: number) {}

    toString(): string {
        return '[' + this.lowerBound + '%, ' + this.upperBound + '%' + (this.upperBound === 100 ? ']' : ')');
    }
}
