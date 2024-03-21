import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a text exercise feedback page.
 */
export class TextExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowTextFeedback(feedbackIndex: number, feedback: string) {
        const feedbackElement = this.page.locator('#text-feedback-' + feedbackIndex);
        await expect(feedbackElement.getByText(feedback)).toBeVisible();
    }
}
