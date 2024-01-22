import { Page } from 'playwright';
import dayjs from 'dayjs/esm';
import { BASE_API } from '../../constants';

export class LectureCreationPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        await this.page.fill('#field_title', title);
    }

    async save() {
        const responsePromise = this.page.waitForResponse(BASE_API + 'lectures');
        await this.page.click('#save-entity');
        return await responsePromise;
    }

    async typeDescription(description: string) {
        const descriptionField = this.page.locator('.ace_content');
        await descriptionField.click();
        await descriptionField.pressSequentially(description);
    }

    async setVisibleDate(date: dayjs.Dayjs) {
        await this.page.fill('#visible-date #date-input-field', date.toString());
    }

    async setStartDate(date: dayjs.Dayjs) {
        await this.page.fill('#start-date #date-input-field', date.toString());
    }

    async setEndDate(date: dayjs.Dayjs) {
        const endDateField = this.page.locator('#end-date #date-input-field');
        await endDateField.fill(' ');
        await endDateField.clear();
        await endDateField.fill(date.toString());
    }
}
