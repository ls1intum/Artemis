import { ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    async provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // Adding feedback requires an empty line. Use fillInlineFeedback to select an existing card explicitly.
        await expect(this.getInlineFeedback(lineIndex)).toHaveCount(0);
        // Monaco renders the code lines, and its decoration API takes a class name and nothing else, so the
        // hover button cannot carry a test id either. These two selectors name Monaco's DOM out of necessity.
        await this.page.locator('.view-line').nth(lineIndex).hover();
        await this.page.locator('.monaco-add-feedback-button').click();
        await this.fillInlineFeedback(lineIndex, 0, points, feedback);
    }

    /**
     * Fills the explicitly selected card (0-based) among the feedback entries on a code line (also 0-based).
     * Inline edits are committed through the card’s Save control.
     */
    async fillInlineFeedback(lineIndex: number, feedbackIndex: number, points: number, feedback: string) {
        const card = this.getInlineFeedback(lineIndex).nth(feedbackIndex);
        const edit = card.getByTestId('feedback-edit');
        if (await edit.isVisible()) {
            await edit.click();
        }
        await card.getByRole('textbox', { name: 'Feedback', exact: true }).fill(feedback);
        await this.setPoints(card, points);
        await card.getByTestId('feedback-save').click();
    }

    private getInlineFeedback(lineIndex: number) {
        return this.page.getByTestId('code-editor-inline-feedback').and(this.page.locator(`[data-code-line="${lineIndex}"]`));
    }

    override async rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }

    override async acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }
}
