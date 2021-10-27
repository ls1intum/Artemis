import { BASE_API, POST } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the text exercise example submission creation page.
 */
export class TextExerciseExampleSubmissionCreationPage {
    getInstructionsRootElement() {
        return cy.get('[jhitranslate="artemisApp.textAssessment.instructions"]').parents('.card');
    }

    typeExampleSubmission(example: string) {
        cy.get('textarea').type(example, { parseSpecialCharSequences: false });
    }

    clickCreateNewExampleSubmission() {
        cy.intercept(POST, BASE_API + 'exercises/*/example-submissions').as('createExampleSubmission');
        cy.get('[data-icon="save"]').click();
        return cy.wait('@createExampleSubmission');
    }

    showsExerciseTitle(exerciseTitle: string) {
        this.getInstructionsRootElement().contains(exerciseTitle).should('be.visible');
    }

    showsProblemStatement(problemStatement: string) {
        this.getInstructionsRootElement().contains(problemStatement).should('be.visible');
    }

    showsExampleSolution(exampleSolution: string) {
        this.getInstructionsRootElement().contains(exampleSolution).should('be.visible');
    }
}
