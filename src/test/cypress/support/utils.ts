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
    const uuid = uuidv4().replace(/-/g, '');
    return uuid.substr(0, 5);
}

/**
 * Allows to enter date into the UI
 * */
export function enterDate(selector: string, date: day.Dayjs) {
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
 * Formats the dayjs object with the time format which the server uses. Also makes sure that dayjs uses the utc timezone.
 * @param dayjs the dayjs object
 * @returns a formatted string representing the date with utc timezone
 */
export function dayjsToString(dayjs: day.Dayjs) {
    // We need to add the Z at the end. Otherwise, the server can't parse it.
    return dayjs.utc().format(TIME_FORMAT) + 'Z';
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

export function titleCaseWord(str: string) {
    str = str.replace('_', ' ');
    const sentence = str.toLowerCase().split(' ');
    for (let i = 0; i < sentence.length; i++) {
        sentence[i] = sentence[i][0].toUpperCase() + sentence[i].slice(1);
    }
    return sentence.join(' ');
}

export function titleLowercase(title: string) {
    return title.replace(' ', '-').toLowerCase();
}

export function getExercise(exerciseId: number) {
    return cy.get(`#exercise-${exerciseId}`);
}

export function parseArrayBufferAsJsonObject(buffer: ArrayBuffer) {
    const bodyString = Cypress.Blob.arrayBufferToBinaryString(buffer);
    return JSON.parse(bodyString);
}
