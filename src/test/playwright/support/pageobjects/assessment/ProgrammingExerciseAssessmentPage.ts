import { ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = '#test-';

    async provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // We can't change elements from the ace editor, so we can't use custom ids here
        const addFeedbackButton = this.page.locator('.ace_gutter-cell').nth(lineIndex).locator('svg');
        await addFeedbackButton.dispatchEvent('click');
        await this.typeIntoFeedbackEditor(feedback, lineIndex);
        await this.typePointsIntoFeedbackEditor(points, lineIndex);
        await this.saveFeedback(lineIndex);
    }

    private async typeIntoFeedbackEditor(text: string, index: number) {
        await this.getInlineFeedback(index).locator('#feedback-textarea').fill(text);
    }

    private async typePointsIntoFeedbackEditor(points: number, index: number) {
        await this.getInlineFeedback(index).locator('#feedback-points').fill(points.toString());
    }

    private async saveFeedback(index: number) {
        await this.getInlineFeedback(index).locator('#feedback-save').click();
    }

    private getInlineFeedback(line: number) {
        return this.page.locator(`#code-editor-inline-feedback-${line}`);
    }

    async rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }

    async acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }
}
