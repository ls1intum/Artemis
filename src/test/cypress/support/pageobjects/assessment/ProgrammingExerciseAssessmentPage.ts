import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';

/**
 * A class which encapsulates UI selectors and actions for the programming exercise assessment page.
 */
export class ProgrammingExerciseAssessmentPage extends AbstractExerciseAssessmentPage {
    readonly feedbackEditorSelector = '.inline-feedback';

    provideFeedbackOnCodeLine(lineIndex: number, points: number, feedback: string) {
        cy.get(`.ace_gutter > .ace_layer > :nth-child(${lineIndex})`).find('[data-icon="plus-square"]').click({ force: true });
        this.typeIntoFeedbackEditor(feedback);
        this.typePointsIntoFeedbackEditor(points);
        this.saveFeedback();
        this.shouldShowEditButtonForCreatedFeedback();
    }

    private typeIntoFeedbackEditor(text: string) {
        cy.get(this.feedbackEditorSelector).find('textarea').should('be.visible').type(text, { parseSpecialCharSequences: false });
    }

    private typePointsIntoFeedbackEditor(points: number) {
        cy.get(this.feedbackEditorSelector).find('[type="number"]').clear().type(points.toString());
    }

    private saveFeedback() {
        cy.get(this.feedbackEditorSelector).find('[jhitranslate="entity.action.save"]').click();
    }

    private shouldShowEditButtonForCreatedFeedback() {
        cy.get(this.feedbackEditorSelector).find('[jhitranslate="entity.action.edit"]').should('be.visible');
    }
}
