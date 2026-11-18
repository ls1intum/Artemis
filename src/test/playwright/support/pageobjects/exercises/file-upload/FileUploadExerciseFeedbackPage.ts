import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a file upload exercise feedback page.
 */
export class FileUploadExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowTextFeedback(feedbackIndex: number, feedback: string) {
        await expect(this.page.locator('#text-feedback-' + feedbackIndex).getByText(feedback)).toBeVisible();
    }
}
