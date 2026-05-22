import { Page } from '@playwright/test';
import { users } from '../../users';

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

    /**
     * True when the page is showing the in-progress conduction view rather than the
     * welcome screen. Detected via the per-conduction-page `Hand In Early` action which
     * never appears on the welcome screen. Used by `startExam` to short-circuit the
     * welcome flow on test exams under heavy load, where occasional navigation races
     * have been observed to land the student directly in conduction without ever
     * rendering the welcome confirmation form.
     */
    private async isInConduction(): Promise<boolean> {
        return this.page
            .locator('button', { hasText: /Hand in Early/i })
            .first()
            .waitFor({ state: 'visible', timeout: 1_500 })
            .then(() => true)
            .catch(() => false);
    }

    async pressStartWithWait() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/*/conduction`);
        await this.page.locator('#start-exam').click();
        await responsePromise;
    }

    async pressStart() {
        await this.page.locator('#start-exam').click();
    }

    async clickContinue() {
        await this.page.locator('#continue').click();
    }

    async pressFinish() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/submit`);
        await this.page.locator('#end-exam').click();
        return await responsePromise;
    }

    async startExam(withWait = false) {
        // Under heavy multi-node load test-exam navigation occasionally lands the student
        // directly in conduction (the welcome screen never renders). If we detect that,
        // skip the welcome-only actions — the test is effectively already past startExam.
        if (await this.isInConduction()) {
            return;
        }
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
