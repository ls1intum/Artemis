import { ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = '#test-';

    provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // We can't change elements from the ace editor, so we can't use custom ids here
        cy.get('.ace_gutter-cell').eq(lineIndex).find('svg').click({ force: true });
        this.typeIntoFeedbackEditor(feedback, lineIndex);
        this.typePointsIntoFeedbackEditor(points, lineIndex);
        this.saveFeedback(lineIndex);
    }

    private typeIntoFeedbackEditor(text: string, index: number) {
        this.getInlineFeedback(index).find('#feedback-textarea').type(text, { parseSpecialCharSequences: false });
    }

    private typePointsIntoFeedbackEditor(points: number, index: number) {
        this.getInlineFeedback(index).find('#feedback-points').clear().type(points.toString());
    }

    private saveFeedback(index: number) {
        this.getInlineFeedback(index).find('#feedback-save').click();
    }

    /**
     * Every code line in the ace editor has an attached inline feedback
     * @param line the code line where the inline feedback is attached
     * @returns the root element of the inline feedback component
     */
    private getInlineFeedback(line: number) {
        return cy.get('#code-editor-inline-feedback-' + line);
    }

    rejectComplaint(response: string, examMode: boolean) {
        return super.rejectComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }

    acceptComplaint(response: string, examMode: boolean) {
        return super.acceptComplaint(response, examMode, ExerciseType.PROGRAMMING);
    }
}
