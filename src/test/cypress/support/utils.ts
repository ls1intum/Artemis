import { TIME_FORMAT } from './constants';
import { v4 as uuidv4 } from 'uuid';
import day from 'dayjs';

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
 * Formats the dayjs object with the time format which the backend uses.
 * @param dayjs the dayjs object
 * @returns a formatted string representing the date
 */
export function dayjsToString(dayjs: day.Dayjs) {
    // We need to add the Z at the end. Otherwise the backend can't parse it.
    return dayjs.format(TIME_FORMAT) + 'Z';
}
