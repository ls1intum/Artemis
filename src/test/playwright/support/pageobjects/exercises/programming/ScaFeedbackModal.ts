import { Page, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async shouldShowPointChart() {
        await expect(this.page.locator('#feedback-chart')).toBeVisible();
    }

    async shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        const feedbackItem = this.page.locator('.feedback-item', { hasText: feedbackText });
        await feedbackItem.scrollIntoViewIfNeeded();
        await expect(feedbackItem).toBeVisible();

        const creditsElement = this.page.locator('.feedback-item', { hasText: feedbackText }).locator('.feedback-item__credits');
        await creditsElement.scrollIntoViewIfNeeded();
        await expect(creditsElement).toContainText(`-${pointReduction}P`);
    }

    async closeModal() {
        // After the migration to PrimeNG DialogService, the inline modal header (with .feedback-header__close)
        // is suppressed in dialog mode in favour of PrimeNG's own header X.
        await this.page.locator('.p-dialog .p-dialog-close-button, .p-dialog [data-pc-section="closebutton"]').first().click();
        await expect(this.page.locator('.result-detail-container')).not.toBeAttached();
    }
}
