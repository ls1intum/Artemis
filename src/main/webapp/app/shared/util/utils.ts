import { omit } from 'lodash';
import { JhiAlert } from 'ng-jhipster';
import * as Sentry from '@sentry/browser';
import { Result } from 'app/entities/result.model';

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
 * @param alert which was sent to the jhiAlertService
 */
export const checkForMissingTranslationKey = (alert: JhiAlert) => {
    if (alert?.msg?.startsWith('translation-not-found')) {
        // In case a translation key is not found, remove the 'translation-not-found[...]' annotation
        const alertMessageMatch = alert.msg.match(/translation-not-found\[(.*?)\]$/);
        if (alertMessageMatch && alertMessageMatch.length > 1) {
            alert.msg = alertMessageMatch[1];
        } else {
            // Fallback, in case the bracket is missing
            alert.msg = alert.msg.replace('translation-not-found', '');
        }
        // Sent a sentry warning with the translation key
        Sentry.captureException(new Error('Unknown translation key: ' + alert.msg));
    }
};
