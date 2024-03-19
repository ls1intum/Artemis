import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class ModelingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowComponentFeedback(component: number, points: number, feedback: string) {
        const feedbackTable = this.page.locator('#component-feedback-table').locator('tbody');
        const componentFeedback = feedbackTable.locator('tr').nth(component);
        await expect(componentFeedback).toContainText(feedback);
        await expect(componentFeedback).toContainText(`${points}`);
    }
}
