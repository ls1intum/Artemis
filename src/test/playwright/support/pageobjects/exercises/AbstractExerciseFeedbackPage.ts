import { BASE_API } from '../../constants';
import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';

/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    protected readonly page: Page;

    readonly RESULT_SELECTOR = '#result';
    readonly ADDITIONAL_FEEDBACK_SELECTOR = '#additional-feedback';

    constructor(page: Page) {
        this.page = page;
    }

    async shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        const additionalFeedbackElement = this.page.locator(this.ADDITIONAL_FEEDBACK_SELECTOR);
        await expect(additionalFeedbackElement.locator('.unified-feedback-points', { hasText: points.toString() })).toBeVisible();
        await expect(additionalFeedbackElement.locator('.unified-feedback-text', { hasText: feedbackText })).toBeVisible();
    }

    async shouldShowScore(percentage: number) {
        const resultPercentage = this.page.locator(this.RESULT_SELECTOR, { hasText: `${percentage}%` });
        await expect(resultPercentage).toBeVisible();
    }

    async complain(complaint: string) {
        const complainButton = this.page.locator('#complain');
        await Commands.reloadUntilFound(this.page, complainButton);
        await complainButton.click();
        await this.page.locator('#complainTextArea').fill(complaint);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/assessment/complaints`);
        await this.page.locator('#submit-complaint').click();
        const response = await responsePromise;
        expect(response.status()).toBe(201);
    }
}
