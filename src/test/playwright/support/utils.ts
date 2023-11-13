import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { v4 as uuidv4 } from 'uuid';
import { Page, expect } from '@playwright/test';
import { TIME_FORMAT } from './constants';

// Add utc plugin to use the utc timezone
dayjs.extend(utc);

/**
 * This file contains all of the global utility functions not directly related to playwright.
 */

/**
 * Generates a unique identifier.
 * */
export function generateUUID() {
    const uuid = uuidv4().replace(/-/g, '');
    return uuid.substr(0, 9);
}

/**
 * Allows to enter date into the UI
 * */
export async function enterDate(page: Page, selector: string, date: dayjs.Dayjs) {
    const dateInputField = page.locator(selector).locator('#date-input-field');
    await expect(dateInputField).toBeEnabled();
    await dateInputField.fill(dayjsToString(date), { force: true });
}

/**
 * Formats the day object with the time format which the server uses. Also makes sure that day uses the utc timezone.
 * @param day the day object
 * @returns a formatted string representing the date with utc timezone
 */
export function dayjsToString(day: dayjs.Dayjs) {
    // We need to add the Z at the end. Otherwise, the server can't parse it.
    return day.utc().format(TIME_FORMAT) + 'Z';
}

/**
 * This function is necessary to make the server and the client date comparable.
 * The server sometimes has 3 digit on the milliseconds and sometimes only 1 digit.
 * With this function we always cut the date string after the first digit.
 * @param date the date as a string
 * @returns a date string with only one digit for the milliseconds
 */
export function trimDate(date: string) {
    return date.slice(0, 19);
}

/**
 * Converts a boolean value to "Yes" if true, or "No" if false.
 * @param boolean - The boolean value to be converted.
 * @returns The corresponding "Yes" or "No" string.
 */
export function convertBooleanToYesNo(boolean: boolean) {
    return boolean ? 'Yes' : 'No';
}
