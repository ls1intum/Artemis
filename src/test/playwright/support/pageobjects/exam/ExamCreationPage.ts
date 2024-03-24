import { Page } from '@playwright/test';
import dayjs from 'dayjs';

import { clearTextField, enterDate } from '../../utils';
import { COURSE_BASE } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the exam creation page.
 */
export class ExamCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Sets the title of the exam.
     * @param title the exam title
     */
    async setTitle(title: string) {
        await this.page.locator('#field_title').clear();
        await this.page.locator('#field_title').fill(title);
    }

    /**
     * Sets exam to test mode
     */
    async setTestMode() {
        await this.page.locator('#exam-mode-picker #test-mode').click();
    }

    /**
     * @param date the date from when the exam should be visible
     */
    async setVisibleDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#visibleDate', date);
    }

    /**
     * @param date the date when the exam starts
     */
    async setStartDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#startDate', date);
    }

    /**
     * @param date the date when the exam will end
     */
    async setEndDate(date: dayjs.Dayjs) {
        await enterDate(this.page, '#endDate', date);
    }

    /**
     * @param time the exam working time
     */
    async setWorkingTime(time: number) {
        await this.page.locator('#workingTimeInMinutes').clear();
        await this.page.locator('#workingTimeInMinutes').fill(time.toString());
    }

    /**
     * Sets the number of exercises in the exam.
     * @param amount the amount of exercises
     */
    async setNumberOfExercises(amount: number) {
        await this.page.locator('#numberOfExercisesInExam').clear();
        await this.page.locator('#numberOfExercisesInExam').fill(amount.toString());
    }

    /**
     * Sets the maximum achievable points in the exam.
     * @param examMaxPoints the max points
     */
    async setExamMaxPoints(examMaxPoints: number) {
        await this.page.locator('#examMaxPoints').clear();
        await this.page.locator('#examMaxPoints').fill(examMaxPoints.toString());
    }

    /**
     * Sets the start text of the exam.
     * @param text the start text
     */
    async setStartText(text: string) {
        await this.enterText('#startText', text);
    }

    /**
     * Sets the end text of the exam.
     * @param text the end text
     */
    async setEndText(text: string) {
        await this.enterText('#endText', text);
    }

    /**
     * Sets the confirmation start text of the exam.
     * @param text the confirmation start text
     */
    async setConfirmationStartText(text: string) {
        await this.enterText('#confirmationStartText', text);
    }

    /**
     * Sets the confirmation end text of the exam.
     * @param text the confirmation end text
     */
    async setConfirmationEndText(text: string) {
        await this.enterText('#confirmationEndText', text);
    }

    /**
     * Submits the created exam.
     * @returns Response object.
     */
    async submit() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams`);
        await this.page.locator('#save-exam').click();
        return await responsePromise;
    }

    /**
     * Updates the created exam.
     * @returns Response object.
     */
    async update() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams`);
        await this.page.locator('#save-exam').click();
        return await responsePromise;
    }

    private async enterText(selector: string, text: string) {
        const textField = this.page.locator(selector);
        const textInput = textField.locator('.ace_text-input');
        // const currentText = textField.locator('.ace_text');

        // if (await currentText.isVisible()) {
        // const text = await currentText.textContent();
        if (text) {
            await clearTextField(textInput);
        }
        // }
        await textInput.pressSequentially(text);
    }
}
