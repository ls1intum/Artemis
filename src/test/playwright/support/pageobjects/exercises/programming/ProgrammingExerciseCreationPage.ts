import { Page, expect } from '@playwright/test';
import { PROGRAMMING_EXERCISE_BASE, ProgrammingLanguage } from '../../../constants';
import { Dayjs } from 'dayjs';

const OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT = 'MMMM D, YYYY';

export class ProgrammingExerciseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    async setShortName(shortName: string) {
        const shortNameField = this.page.locator('#field_shortName');
        await shortNameField.clear();
        await shortNameField.fill(shortName);
    }

    async setProgrammingLanguage(programmingLanguage: ProgrammingLanguage) {
        await this.page.locator('#field_programmingLanguage').selectOption(programmingLanguage);
    }

    async setPackageName(packageName: string) {
        const packageNameField = this.page.locator('#field_packageName');
        await packageNameField.clear();
        await packageNameField.fill(packageName);
    }

    async setPoints(points: number) {
        const pointsField = this.page.locator('#field_points');
        await pointsField.clear();
        await pointsField.fill(points.toString());
    }

    async checkAllowOnlineEditor() {
        await this.page.locator('#field_allowOnlineEditor').check();
    }

    async generate() {
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/setup`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/import/*`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    /**
     * Sets the Due Date field by using the owl datepicker
     * @param date
     * */
    async setDueDate(date: Dayjs) {
        await this.page.locator('#programming-exercise-due-date-picker').click();

        // Makes sure that popup is visible before we choose a date
        await this.page.locator('.owl-dt-popup').waitFor({ state: 'visible' });

        const ariaLabelDate = date.format(OWL_DATEPICKER_ARIA_LABEL_DATE_FORMAT);
        await this.page.locator(`td[aria-label="${ariaLabelDate}"]`).click();

        // Ensure the date picker is closed after setting the date
        while (await this.page.locator('.owl-dt-popup').isVisible()) {
            await this.page.locator('.owl-dt-control-content.owl-dt-control-button-content', { hasText: 'Set' }).dispatchEvent('click');
            await this.page.waitForTimeout(500);
        }
        await expect(this.page.locator('.owl-dt-popup')).not.toBeVisible();
    }
}
