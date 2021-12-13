import { CypressExerciseType } from '../../requests/CourseManagementRequests';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = '#test-';

    provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // We can't change elements from the ace editor, so we can't use custom ids here
        cy.get('.ace_gutter-cell').eq(lineIndex).find('[data-icon="plus-square"]').click({ force: true });
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
     * @param index the index of the code line where the inline feedback is attached
     * @returns the root element of the inline feedback component
     */
    private getInlineFeedback(index: number) {
        return cy.get('#test-' + index);
    }

    rejectComplaint(response: string) {
        return super.rejectComplaint(response, CypressExerciseType.PROGRAMMING);
    }

    acceptComplaint(response: string) {
        return super.acceptComplaint(response, CypressExerciseType.PROGRAMMING);
    }
}
