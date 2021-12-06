import { CypressExerciseType } from '../../requests/CourseManagementRequests';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = '.inline-feedback';

    provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        // We can't adjust change elements from the ace editor, so we can't use custom ids here
        cy.get(`.ace_gutter > .ace_layer > :nth-child(${lineIndex})`).find('[data-icon="plus-square"]').click({ force: true });
        this.typeIntoFeedbackEditor(feedback);
        this.typePointsIntoFeedbackEditor(points);
        this.saveFeedback();
    }

    private typeIntoFeedbackEditor(text: string) {
        cy.get('#feedback-text-area').type(text, { parseSpecialCharSequences: false });
    }

    private typePointsIntoFeedbackEditor(points: number) {
        cy.get('#feedback-points').clear().type(points.toString());
    }

    private saveFeedback() {
        cy.get('#feedback-save').click();
    }

    rejectComplaint(response: string) {
        return super.rejectComplaint(response, CypressExerciseType.PROGRAMMING);
    }

    acceptComplaint(response: string) {
        return super.acceptComplaint(response, CypressExerciseType.PROGRAMMING);
    }
}
