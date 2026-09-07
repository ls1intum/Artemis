import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * UI selectors and actions for a modeling exercise feedback page.
 *
 * Overrides the shared accessors because the modeling assessed view renders feedback as rows in the
 * editor's own chrome rather than in the `unified-feedback` cards the other exercise types use.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        const row = this.page.locator('[data-testid="component-feedback-table"]').locator('.feedback-row').nth(component);
        await expect(row.locator('.feedback-row__score', { hasText: points.toString() })).toBeVisible();
        await expect(row.locator('.feedback-row__text', { hasText: feedback })).toBeVisible();
    }

    override async shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        const rows = this.page.locator(this.ADDITIONAL_FEEDBACK_SELECTOR).locator('.feedback-row');
        await expect(rows.locator('.feedback-row__score', { hasText: points.toString() })).toBeVisible();
        await expect(rows.locator('.feedback-row__text', { hasText: feedbackText })).toBeVisible();
    }
}
