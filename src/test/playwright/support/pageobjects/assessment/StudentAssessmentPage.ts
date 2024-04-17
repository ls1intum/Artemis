import { Page } from 'playwright';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the student assessment page.
 */
export class StudentAssessmentPage {
    protected readonly page: Page;
    private complaintResponseSelector = '#complainResponseTextArea';

    constructor(page: Page) {
        this.page = page;
    }

    async startComplaint() {
        await this.page.locator('#complain').click({ timeout: 30000 });
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
