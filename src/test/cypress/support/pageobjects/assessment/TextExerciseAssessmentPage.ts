import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the text exercise assessment page.
 */
export class TextExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = 'jhi-textblock-feedback-editor';

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
        const request = super.submit();
        cy.contains('Your assessment was submitted successfully!').should('be.visible');
        return request;
    }
}
