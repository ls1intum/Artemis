import { omit, sum } from 'lodash-es';
import { captureException } from '@sentry/angular';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export function cleanString(str?: string): string {
    if (!str) {
        return '';
    }
    return str.toLowerCase().replaceAll(' ', '').replaceAll('_', '').replaceAll('-', '').trim();
}

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
export const stringifyIgnoringFields = <T extends object>(val: T, ...ignoredFields: string[]): string => {
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
export const roundScorePercentSpecifiedByCourseSettings = (relativeScore: any, course: Course | undefined) => {
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
export const roundValueSpecifiedByCourseSettings = (value: any, course: Course | undefined) => {
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
export const findLatestResult = (results: Result[] | undefined) => {
    return results?.length ? results.reduce((current, result) => (current.id! > result.id! ? current : result)) : undefined;
};

export const isDate = (input: any) => {
    return input instanceof Date || Object.prototype.toString.call(input) === '[object Date]';
};

/**
 * Represents the inclusive range with a lower and upper bound
 */
export class Range {
    constructor(
        public lowerBound: number,
        public upperBound: number,
    ) {}

    toString(): string {
        return '[' + this.lowerBound + '%, ' + this.upperBound + '%' + (this.upperBound === 100 ? ']' : ')');
    }
}

export function getAsMutableObject(object: any) {
    return { ...object };
}

/**
 * Usages:
 * - when the router keeps the position from the previous page for the new page
 * - to make sure that a message from the {@link AlertService} is recognized by the user
 */
export function scrollToTopOfPage() {
    // The window itself cannot be scrolled; overflowing content is handled by the page wrapper.
    const pageWrapper = document.getElementById('page-wrapper');
    if (pageWrapper) {
        pageWrapper.scroll(0, 0);
    }
}

/**
 * For exam exercises the course will not be set as exam exercises are linked via exercise groups.
 *
 * @param exercise for which is checked if it belongs to an exam
 */
export function isExamExercise(exercise: Exercise) {
    return exercise.course === undefined;
}

/**
 * Rounds a value up to the nearest multiple
 *
 * @param value    that shall be rounded
 * @param multiple to which we round up
 * @param roundUp  if true, we round up, otherwise we round down
 */
export function roundToNextMultiple(value: number, multiple: number, roundUp: boolean) {
    if (roundUp) {
        return Math.ceil(value / multiple) * multiple;
    }

    return Math.floor(value / multiple) * multiple;
}

export function removeSpecialCharacters(input: string): string {
    return input.replace(/[^a-zA-Z0-9]/g, '');
}

const lookup = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
const reverseLookup = new Uint8Array(256);

for (let i = 0; i < lookup.length; i++) {
    reverseLookup[lookup.charCodeAt(i)] = i;
}

// @ts-ignore // TODO add source (sample project)
export function decodeBase64url(base64url: any) {
    const base64urlLength = base64url.length;

    const placeHolderLength = base64url.charAt(base64urlLength - 2) === '=' ? 2 : base64url.charAt(base64urlLength - 1) === '=' ? 1 : 0;
    const bufferLength = (base64urlLength * 3) / 4 - placeHolderLength;

    const arrayBuffer = new ArrayBuffer(bufferLength);
    const uint8Array = new Uint8Array(arrayBuffer);

    let j = 0;
    for (let i = 0; i < base64urlLength; i += 4) {
        const tmp0 = reverseLookup[base64url.charCodeAt(i)];
        const tmp1 = reverseLookup[base64url.charCodeAt(i + 1)];
        const tmp2 = reverseLookup[base64url.charCodeAt(i + 2)];
        const tmp3 = reverseLookup[base64url.charCodeAt(i + 3)];

        uint8Array[j++] = (tmp0 << 2) | (tmp1 >> 4);
        uint8Array[j++] = ((tmp1 & 15) << 4) | (tmp2 >> 2);
        uint8Array[j++] = ((tmp2 & 3) << 6) | (tmp3 & 63);
    }

    return arrayBuffer;
}

// @ts-ignore
export function encodeBase64url(arrayBuffer: any) {
    const uint8Array = new Uint8Array(arrayBuffer);
    const length = uint8Array.length;
    let base64url = '';

    for (let i = 0; i < length; i += 3) {
        base64url += lookup[uint8Array[i] >> 2];
        base64url += lookup[((uint8Array[i] & 3) << 4) | (uint8Array[i + 1] >> 4)];
        base64url += lookup[((uint8Array[i + 1] & 15) << 2) | (uint8Array[i + 2] >> 6)];
        base64url += lookup[uint8Array[i + 2] & 63];
    }

    switch (length % 3) {
        case 1:
            base64url = base64url.substring(0, base64url.length - 2);
            break;
        case 2:
            base64url = base64url.substring(0, base64url.length - 1);
            break;
    }
    return base64url;
}
