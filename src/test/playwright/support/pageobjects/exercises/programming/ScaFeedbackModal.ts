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

    // TODO: Check if currently used locators are necessary in Playwright
    async shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        const feedbackTextElement = this.page.locator('.feedback-list').getByText(feedbackText);
        await feedbackTextElement.scrollIntoViewIfNeeded();
        await expect(feedbackTextElement).toBeVisible();

        const creditsElement = this.page.locator('.feedback-item', { hasText: feedbackText }).locator('.feedback-item__credits');
        await creditsElement.scrollIntoViewIfNeeded();
        await expect(creditsElement).toContainText(`-${pointReduction}P`);
        await expect(creditsElement).toBeVisible();
    }

    async closeModal() {
        await this.page.locator('.feedback-header__close').click();
        await expect(this.page.locator('.result-detail-container')).not.toBeAttached();
    }
}
