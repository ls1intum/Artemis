import { Page } from 'playwright';
import dayjs from 'dayjs';
import { BASE_API } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the Lecture Creation Page.
 */
export class LectureCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Fills in the lecture title input field with the provided title.
     * @param title - The title to set for the lecture.
     */
    async setTitle(title: string) {
        await this.page.fill('#field_title', title);
    }

    /**
     * Clicks the save button to submit the lecture creation form and waits for the API response.
     * @returns A promise that resolves with the response of the lecture creation API call.
     */
    async save() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}/lectures`);
        await this.page.click('#save-entity');
        return await responsePromise;
    }

    /**
     * Types the provided description into the lecture's description field.
     * @param description - The description text for the lecture.
     */
    async typeDescription(description: string) {
        const descriptionField = this.page.locator('.ace_content');
        await descriptionField.click();
        await descriptionField.pressSequentially(description);
    }

    /**
     * Sets the visibility date of the lecture to the provided date.
     * @param date - The date when the lecture should become visible.
     */
    async setVisibleDate(date: dayjs.Dayjs) {
        await this.page.fill('#visible-date #date-input-field', date.toString());
    }

    /**
     * Sets the start date of the lecture to the provided date.
     * @param date - The start date for the lecture.
     */
    async setStartDate(date: dayjs.Dayjs) {
        await this.page.fill('#start-date #date-input-field', date.toString());
    }

    /**
     * Sets the end date of the lecture to the provided date.
     * @param date - The end date for the lecture.
     */
    async setEndDate(date: dayjs.Dayjs) {
        const endDateField = this.page.locator('#end-date #date-input-field');
        await endDateField.fill(' ');
        await endDateField.clear();
        await endDateField.fill(date.toString());
    }
}
