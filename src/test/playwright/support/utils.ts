import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { v4 as uuidv4 } from 'uuid';
import { Browser, Locator, Page, expect } from '@playwright/test';
import { TIME_FORMAT } from './constants';

// Add utc plugin to use the utc timezone
dayjs.extend(utc);

/*
 * This file contains all the global utility functions.
 */

/**
 * Generates a unique identifier.
 */
export function generateUUID() {
    const uuid = uuidv4().replace(/-/g, '');
    return uuid.substr(0, 9);
}

/**
 * Allows to enter date into the UI
 */
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
 * Converts a snake_case word to Title Case (each word's first letter capitalized and spaces in between).
 * @param str - The snake_case word to be converted to Title Case.
 * @returns The word in Title Case.
 */
export function titleCaseWord(str: string) {
    str = str.replace('_', ' ');
    const sentence = str.toLowerCase().split(' ');
    for (let i = 0; i < sentence.length; i++) {
        sentence[i] = sentence[i][0].toUpperCase() + sentence[i].slice(1);
    }
    return sentence.join(' ');
}

/**
 * Retrieves the DOM element representing the exercise with the specified ID.
 * @param page - Playwright Page instance used during the test.
 * @param exerciseId - The ID of the exercise for which to retrieve the DOM element.
 * @returns Locator that yields the DOM element representing the exercise.
 */
export function getExercise(page: Page, exerciseId: number) {
    return page.locator(`#exercise-${exerciseId}`);
}

/**
 * Converts a title to lowercase and replaces spaces with hyphens.
 * @param title - The title to be converted to lowercase with hyphens.
 * @returns The converted title in lowercase with hyphens.
 */
export function titleLowercase(title: string) {
    return title.replace(' ', '-').toLowerCase();
}

/**
 * Converts a boolean value to its related icon class.
 * @param boolean - The boolean value to be converted.
 * @returns The corresponding ".checked" or ".unchecked" string.
 */
export function convertBooleanToCheckIconClass(boolean: boolean) {
    return boolean ? '.checked' : '.unchecked';
}

export async function clearTextField(textField: Locator) {
    await textField.click({ clickCount: 4, force: true });
    await textField.press('Backspace');
}

export async function hasAttributeWithValue(page: Page, selector: string, value: string): Promise<boolean> {
    return page.evaluate(
        ({ selector, value }) => {
            const element = document.querySelector(selector);
            if (!element) return false;
            for (const attr of element.attributes) {
                if (attr.value === value) {
                    return true;
                }
            }
            return false;
        },
        { selector, value },
    );
}

export function parseNumber(text?: string): number | undefined {
    return text ? parseInt(text) : undefined;
}

export async function newBrowserPage(browser: Browser) {
    const context = await browser.newContext();
    return await context.newPage();
}
