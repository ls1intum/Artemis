import { BASE_API } from '../../constants';
import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';

/**
 * Parent class for all exercise feedback pages (/course/./exercise/./participate/.)
 */
export abstract class AbstractExerciseFeedback {
    protected readonly page: Page;

    readonly resultSelector = '#result';
    readonly additionalFeedbackSelector = '#additional-feedback';
    readonly complainButtonSelector = '#complain';

    constructor(page: Page) {
        this.page = page;
    }

    async shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        const additionalFeedbackElement = this.page.locator(this.additionalFeedbackSelector);
        if (Math.abs(points) === 1) {
            await expect(additionalFeedbackElement.getByText(`${points} Point: ${feedbackText}`)).toBeVisible();
        } else {
            await expect(additionalFeedbackElement.getByText(`${points} Points: ${feedbackText}`)).toBeVisible();
        }
    }

    async shouldShowScore(percentage: number) {
        const resultPercentage = this.page.locator(this.resultSelector, { hasText: `${percentage}%` });
        await expect(resultPercentage).toBeVisible();
    }

    async complain(complaint: string) {
        await Commands.reloadUntilFound(this.page, this.complainButtonSelector);
        await this.page.locator(this.complainButtonSelector).click();
        await this.page.locator('#complainTextArea').fill(complaint);
        const responsePromise = this.page.waitForResponse(`${BASE_API}/complaints`);
        await this.page.locator('#submit-complaint').click();
        const response = await responsePromise;
        expect(response.status()).toBe(201);
    }
}
