import { AbstractExerciseFeedback } from '../AbstractExerciseFeedbackPage';
import { OnlineEditorPage } from './OnlineEditorPage';
import { Commands } from '../../../commands';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for a programming exercise feedback page.
 */
export class ProgrammingExerciseFeedbackPage extends AbstractExerciseFeedback {
    async shouldShowAdditionalFeedback(points: number, feedbackText: string) {
        await Commands.reloadUntilFound(this.page, this.additionalFeedbackSelector);
        await expect(this.page.locator(this.additionalFeedbackSelector).getByText(`${points} Points: ${feedbackText}`)).toBeVisible();
    }

    async shouldShowCodeFeedback(exerciseID: number, filename: string, feedback: string, points: string, editorPage: OnlineEditorPage) {
        await editorPage.openFileWithName(exerciseID, filename);
        const feedbackElement = await this.findVisibleInlineFeedback();
        await expect(feedbackElement.getByText(feedback)).toBeVisible();
        await expect(feedbackElement.getByText(`${points}P`)).toBeVisible();
    }

    private async findVisibleInlineFeedback() {
        // The direct conversion of the cy.get() with should('be.visible') to Playwright.
        // Playwright handles visibility checks differently, so the check is incorporated into the expect statement.
        // Note: Playwright's visibility checks are more strict than Cypress's.
        // If the element's visibility varies dynamically, you may need to adjust the logic.
        const feedbackElement = this.page.locator('[id*="code-editor-inline-feedback-"]');
        await expect(feedbackElement).toBeVisible();
        return feedbackElement;
    }

    async shouldShowRepositoryLockedWarning() {
        await expect(this.page.locator('#repository-locked-warning')).toBeVisible();
    }
}
