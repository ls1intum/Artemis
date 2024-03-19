import { Page } from '@playwright/test';
import { COURSE_BASE } from '../../constants';
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

    async pressStartWithWait() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/student-exams/*/conduction`);
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
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/student-exams/submit`);
        await this.page.locator('#end-exam').click();
        return await responsePromise;
    }

    async startExam(withWait = false) {
        await this.setConfirmCheckmark();
        await this.enterFirstnameLastname();
        if (withWait) {
            await this.pressStartWithWait();
        } else {
            await this.pressStart();
        }
    }

    async finishExam(timeout?: number) {
        await this.setConfirmCheckmark(timeout);
        await this.enterFirstnameLastname();
        return await this.pressFinish();
    }

    async pressShowSummary() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/student-exams/*/summary`);
        await this.page.locator('#showExamSummaryButton').click();
        await responsePromise;
    }
}
