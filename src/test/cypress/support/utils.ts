import { TIME_FORMAT } from './constants';
import dayjs from 'dayjs/esm';
import utc from 'dayjs/esm/plugin/utc';
import { v4 as uuidv4 } from 'uuid';

// Add utc plugin to use the utc timezone
dayjs.extend(utc);

/**
 * This file contains all of the global utility functions not directly related to cypress.
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
export function enterDate(selector: string, date: dayjs.Dayjs) {
    const dateInputField = cy.get(selector).find('#date-input-field');
    dateInputField.should('not.be.disabled');
    dateInputField.clear().type(dayjsToString(date), { force: true });
}

/**
 * Allows to check a specified input field for a value
 * */
export function checkField(field: string, value: any) {
    cy.get(field).should('have.value', value);
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
 * Converts the response object obtained from a multipart request to an entity object.
 * @param response - The Cypress.Response<T> object obtained from a multipart request.
 * @returns The entity object parsed from the response.
 */
export function convertModelAfterMultiPart<T>(response: Cypress.Response<T>): T {
    // Cypress currently has some issues with our multipart request, parsing this not as an object but as an ArrayBuffer
    // Once this is fixed (and hence the expect statements below fail), we can remove the additional parsing
    expect(response.body).not.to.be.an('object');
    expect(response.body).to.be.an('ArrayBuffer');

    return parseArrayBufferAsJsonObject(response.body as ArrayBuffer);
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
 * Converts a title to lowercase and replaces spaces with hyphens.
 * @param title - The title to be converted to lowercase with hyphens.
 * @returns The converted title in lowercase with hyphens.
 */
export function titleLowercase(title: string) {
    return title.replace(' ', '-').toLowerCase();
}

/**
 * Retrieves the DOM element representing the exercise with the specified ID.
 * @param exerciseId - The ID of the exercise for which to retrieve the DOM element.
 * @returns A Cypress.Chainable that yields the DOM element representing the exercise.
 */
export function getExercise(exerciseId: number) {
    return cy.get(`#exercise-${exerciseId}`);
}

/**
 * Converts a boolean value to its related icon class.
 * @param boolean - The boolean value to be converted.
 * @returns The corresponding ".checked" or ".unchecked" string.
 */
export function convertBooleanToCheckIconClass(boolean: boolean) {
    return boolean ? '.checked' : '.unchecked';
}

/**
 * Parses an ArrayBuffer as a JSON object.
 * @param buffer - The ArrayBuffer to be parsed as a JSON object.
 * @returns The parsed JSON object.
 */
function parseArrayBufferAsJsonObject(buffer: ArrayBuffer) {
    const bodyString = Cypress.Blob.arrayBufferToBinaryString(buffer);
    return JSON.parse(bodyString);
}
