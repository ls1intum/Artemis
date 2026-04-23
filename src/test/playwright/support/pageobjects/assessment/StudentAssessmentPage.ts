import { Page } from 'playwright';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the student assessment page.
 */
export class StudentAssessmentPage {
    protected readonly page: Page;
    constructor(page: Page) {
        this.page = page;
    }

    async startComplaint() {
        // Wait for the page to fully load before looking for the complaint button.
        // The button depends on async accountService.identity() resolving to set
        // isCorrectUserToFileAction, and the exam review period must be active.
        const complainButton = this.page.locator('#complain');
        await complainButton.waitFor({ state: 'visible', timeout: 30000 });
        await complainButton.click();
    }

    async enterComplaint(text: string) {
        await this.page.locator('#complainTextArea').fill(text);
    }

    async submitComplaint() {
        await this.page.locator('#submit-complaint').click();
    }

    getComplaintBadge() {
        return this.page.locator('jhi-complaint-request .badge');
    }

    getComplaintResponse() {
        return this.page.locator('#complainResponseTextArea');
    }

    async checkComplaintStatusText(text: string) {
        await expect(this.getComplaintBadge().filter({ hasText: text })).toBeAttached();
    }

    async checkComplaintResponseText(text: string) {
        await expect(this.getComplaintResponse()).toHaveValue(text);
    }
}
