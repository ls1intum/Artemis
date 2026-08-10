import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a modeling exercise feedback page.
 *
 * The modeling assessed view renders its feedback inside the editor's own chrome
 * using Apollon's assessment language — a tone badge carrying the signed score,
 * the element as the diagram names it, then the comment — rather than the shared
 * unified-feedback cards the other exercise types still use.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        const row = this.page.locator('#component-feedback-table').locator('.feedback-row').nth(component);
        await expect(row.locator('.feedback-row__score', { hasText: points.toString() })).toBeVisible();
        await expect(row.locator('.feedback-row__text', { hasText: feedback })).toBeVisible();
    }

    override async shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        const rows = this.page.locator(this.ADDITIONAL_FEEDBACK_SELECTOR).locator('.feedback-row');
        await expect(rows.locator('.feedback-row__score', { hasText: points.toString() })).toBeVisible();
        await expect(rows.locator('.feedback-row__text', { hasText: feedbackText })).toBeVisible();
    }
}
