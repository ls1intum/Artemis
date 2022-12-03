import utc from 'dayjs/esm/plugin/utc';
import { TIME_FORMAT } from './constants';
import { v4 as uuidv4 } from 'uuid';
import day from 'dayjs/esm';

// Add utc plugin to use the utc timezone
day.extend(utc);

/**
 * This file contains all of the global utility functions not directly related to cypress.
 */

/**
 * Generates a unique identifier.
 * */
export function generateUUID() {
    return uuidv4().replace(/-/g, '');
}

/**
 * Formats the dayjs object with the time format which the server uses. Also makes sure that dayjs uses the utc timezone.
 * @param dayjs the dayjs object
 * @returns a formatted string representing the date with utc timezone
 */
export function dayjsToString(dayjs: day.Dayjs) {
    // We need to add the Z at the end. Otherwise, the server can't parse it.
    return dayjs.utc().format(TIME_FORMAT) + 'Z';
}

export function parseArrayBufferAsJsonObject(buffer: ArrayBuffer) {
    const bodyString = Cypress.Blob.arrayBufferToBinaryString(buffer);
    return JSON.parse(bodyString);
}
