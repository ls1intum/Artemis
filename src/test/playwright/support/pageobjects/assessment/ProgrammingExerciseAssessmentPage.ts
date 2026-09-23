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
        // Manual inline feedback has no save button - typing the description and points commits it immediately.
        await this.typeIntoFeedbackEditor(feedback, lineIndex);
        await this.typePointsIntoFeedbackEditor(points, lineIndex);
    }

    private async typeIntoFeedbackEditor(text: string, index: number) {
        await this.getInlineFeedback(index).locator('.unified-feedback-detail-input').fill(text);
    }

    private async typePointsIntoFeedbackEditor(points: number, index: number) {
        await this.setPointsViaStepper(this.getInlineFeedback(index), points);
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
