import { ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    async provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // Monaco renders the code lines, and its decoration API takes a class name and nothing else, so the
        // hover button cannot carry a test id either. These two selectors name Monaco's DOM out of necessity.
        await this.page.locator('.view-line').nth(lineIndex).hover();
        await this.page.locator('.monaco-add-feedback-button').click();
        await this.typeIntoFeedbackEditor(feedback, lineIndex);
        await this.typePointsIntoFeedbackEditor(points, lineIndex);
        await this.getInlineFeedback(lineIndex).getByTestId('feedback-save').click();
    }

    private async typeIntoFeedbackEditor(text: string, index: number) {
        await this.getInlineFeedback(index).getByTestId('feedback-editor-text-input').fill(text);
    }

    private async typePointsIntoFeedbackEditor(points: number, index: number) {
        const pointsInput = this.getInlineFeedback(index).getByTestId('feedback-editor-points-input');
        await pointsInput.fill(points.toString());
        await pointsInput.blur();
    }

    private getInlineFeedback(line: number) {
        return this.page.locator(`#code-editor-inline-feedback-${line}`);
    }

    override async rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }

    override async acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }
}
