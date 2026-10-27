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

    async fillInlineFeedback(lineIndex: number, feedbackIndex: number, points: number, feedback: string) {
        const card = this.getInlineFeedback(lineIndex).nth(feedbackIndex);
        await card.getByTestId('feedback-editor-text-input').fill(feedback);
        const pointsInput = card.getByTestId('feedback-editor-points-input');
        await pointsInput.fill(points.toString());
        await pointsInput.blur();
        await card.getByTestId('feedback-save').click();
    }

    private getInlineFeedback(line: number) {
        return this.page.getByTestId('code-editor-inline-feedback').and(this.page.locator(`[data-code-line="${line}"]`));
    }

    override async rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }

    override async acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }
}
