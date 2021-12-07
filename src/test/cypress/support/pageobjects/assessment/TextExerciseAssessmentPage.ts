import { POST, BASE_API } from '../../constants';
import { CypressExerciseType } from '../../requests/CourseManagementRequests';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the text exercise assessment page.
 */
export class TextExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = 'jhi-textblock-feedback-editor';

    getInstructionsRootElement() {
        return cy.get('#instructions-card');
    }

    provideFeedbackOnTextSection(section: string, points: number, feedback: string) {
        cy.contains(section).parents('jhi-textblock-assessment-card').first().click();
        this.typeIntoFeedbackEditor(feedback);
        this.typePointsIntoFeedbackEditor(points);
    }

    private typeIntoFeedbackEditor(text: string) {
        cy.get(this.feedbackEditorSelector).find('textarea').type(text, { parseSpecialCharSequences: false });
    }

    private typePointsIntoFeedbackEditor(points: number) {
        cy.get(this.feedbackEditorSelector).find('[type="number"]').clear().type(points.toString());
    }

    submit() {
        // Feedback route is special for text exercises so we override parent here...
        cy.intercept(POST, BASE_API + 'participations/*/results/*/submit-text-assessment').as('submitFeedback');
        cy.get('[jhitranslate="entity.action.submit"]').click();
        return cy.wait('@submitFeedback');
    }

    rejectComplaint(response: string) {
        return super.rejectComplaint(response, CypressExerciseType.TEXT);
    }

    acceptComplaint(response: string) {
        return super.acceptComplaint(response, CypressExerciseType.TEXT);
    }
}
