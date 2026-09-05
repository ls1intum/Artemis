import { Page, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Verifies that the feedback modal (the FeedbackComponent / result-detail view) rendered its full result view:
     * the loading spinner is gone, the result-detail container is shown (not the empty "no result details" fallback),
     * and the feedback list rendered at least the expected number of feedback items.
     */
    async shouldRenderFeedbackDetails(minimumFeedbackItems = 1) {
        await expect(this.page.locator('[data-testid="result-detail-spinner"]')).toBeHidden();
        await expect(this.page.locator('.result-detail-container')).toBeVisible();
        // The empty-state fallback ("No result details available.") must not be shown when feedback is present.
        await expect(this.page.getByText('No result details available.')).toBeHidden();
        const feedbackList = this.page.locator('.feedback-list');
        await expect(feedbackList).toBeVisible();
        const feedbackItems = feedbackList.locator('.feedback-item');
        await expect(feedbackItems.first()).toBeVisible();
        expect(await feedbackItems.count()).toBeGreaterThanOrEqual(minimumFeedbackItems);
    }

    async shouldShowPointChart() {
        await expect(this.page.locator('[data-testid="feedback-chart"]')).toBeVisible();
    }

    /**
     * Verifies the programming-specific header the FeedbackComponent renders for a programming result: the
     * "submitted … linked to commit" line with a non-empty commit hash.
     */
    async shouldShowCommitHash() {
        const commitLine = this.page.locator('.result-detail-container p', { hasText: 'linked to commit' });
        await expect(commitLine).toBeVisible();
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
        await this.page.locator('[role="dialog"] [data-pc-name="pcclosebutton"]').first().click();
        await expect(this.page.locator('.result-detail-container')).not.toBeAttached();
    }
}
