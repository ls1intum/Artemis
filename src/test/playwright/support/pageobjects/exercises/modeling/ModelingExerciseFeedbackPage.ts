import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * UI selectors and actions for a modeling exercise feedback page.
 *
 * Element-linked feedback still needs its own accessor (`shouldShowComponentFeedback`, scoped by
 * diagram-element index within `component-feedback-table` — a concept the shared base class has no
 * notion of), but both referenced and unreferenced feedback now render through the same
 * `unified-feedback` card every other exercise type uses, so `shouldShowAdditionalFeedback` needs no
 * override here anymore.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        const row = this.page.locator('[data-testid="component-feedback-table"]').locator('.feedback-row').nth(component);
        await expect(row.locator('.unified-feedback-points', { hasText: points.toString() })).toBeVisible();
        await expect(row.locator('.unified-feedback-text', { hasText: feedback })).toBeVisible();
    }
}
