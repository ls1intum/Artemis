import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { v4 as uuidv4 } from 'uuid';
import { TIME_FORMAT } from './constants';
import * as fs from 'fs';
import { dirname } from 'path';
import { Browser, Locator, Page, expect } from '@playwright/test';

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
 * @returns The corresponding icon class
 */
export function convertBooleanToCheckIconClass(boolean: boolean) {
    const sectionInvalidIcon = '.fa-xmark';
    const sectionValidIcon = '.fa-circle-check';
    return boolean ? sectionValidIcon : sectionInvalidIcon;
}

/**
 * Convert a base64-encoded string to a `Blob`.
 *
 * This is an adaptation of the `base64StringToBlob` function from `blob-util` library.
 * Since Playwright has no access to DOM APIs, we cannot use the one in `blob-util` library as it uses `window` object.
 *
 * Example:
 *
 * ```js
 * var blob = blobUtil.base64StringToBlob(base64String);
 * ```
 * @param base64 - base64-encoded string
 * @param type - the content type (optional)
 * @returns Blob
 */
export function base64StringToBlob(base64: string, type?: string): Blob {
    const buffer = Buffer.from(base64!, 'base64');
    return new Blob([buffer], { type });
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

export async function createFileWithContent(filePath: string, content: string) {
    const directory = dirname(filePath);

    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory, { recursive: true });
    }
    fs.writeFileSync(filePath, content);
}

export async function newBrowserPage(browser: Browser) {
    const context = await browser.newContext();
    return await context.newPage();
}

/**
 * Drags an element to a droppable element.
 * @param page - Playwright Page instance used during the test.
 * @param draggable - Locator of the element to be dragged.
 * @param droppable - Locator of the element to be dropped on.
 */
export async function drag(page: Page, draggable: Locator, droppable: Locator) {
    const box = (await droppable.boundingBox())!;
    await draggable.hover();

    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, {
        steps: 5,
    });
    await page.mouse.up();
}
