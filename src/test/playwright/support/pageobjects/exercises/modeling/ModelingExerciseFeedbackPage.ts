import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        const feedbackTable = this.page.locator('#component-feedback-table');
        const componentFeedback = feedbackTable.locator('.unified-feedback').nth(component);
        await expect(componentFeedback.locator('.unified-feedback-title', { hasText: feedback })).toBeVisible();
        await expect(componentFeedback.locator('.unified-feedback-points', { hasText: points.toString() })).toBeVisible();
    }
}
