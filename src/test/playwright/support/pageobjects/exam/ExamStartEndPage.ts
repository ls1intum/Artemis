import { Page, expect } from '@playwright/test';
import { users } from '../../users';
import { RELOAD_RENDER_TIMEOUT } from '../../timeouts';

export class ExamStartEndPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async enterFirstnameLastname() {
        const account = await users.getAccountInfo(this.page.request);
        await this.page.locator('#fullname').fill((account.firstName ?? '') + ' ' + (account.lastName ?? ''));
    }

    async setConfirmCheckmark(timeout?: number) {
        await this.page.locator('#confirmBox').check({ timeout: timeout });
    }

    async pressStartWithWait() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/*/conduction`);
        await this.page.locator('[data-testid="start-exam"]').click();
        await responsePromise;
    }

    async pressStart() {
        await this.page.locator('[data-testid="start-exam"]').click();
    }

    async clickContinue() {
        await this.page.locator('#continue').click();
    }

    async pressFinish() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/submit`);
        await this.page.locator('[data-testid="end-exam"]').click();
        return await responsePromise;
    }

    /**
     * Starts the exam from its welcome screen. The screen has to be there: a student who lands somewhere else (already inside
     * the exam, or on another route) means the test is not in the state it thinks it is, and skipping the welcome steps would
     * hide that.
     */
    async startExam(withWait = false) {
        await expect(this.page.locator('#confirmBox')).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        await this.setConfirmCheckmark();
        await this.enterFirstnameLastname();
        if (withWait) {
            await this.pressStartWithWait();
        } else {
            await this.pressStart();
        }
    }

    async onlyClickConfirmationCheckmark() {
        await this.setConfirmCheckmark();
    }

    async finishExam(timeout?: number) {
        await this.setConfirmCheckmark(timeout);
        await this.enterFirstnameLastname();
        return await this.pressFinish();
    }

    async pressShowSummary() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/*/summary`);
        await this.page.locator('#showExamSummaryButton').click();
        await responsePromise;
    }
}
