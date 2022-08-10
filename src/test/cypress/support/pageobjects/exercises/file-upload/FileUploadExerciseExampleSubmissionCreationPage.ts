import { BASE_API, POST } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the file upload exercise example submission creation page.
 */
export class FileUploadExerciseExampleSubmissionCreationPage {
    getInstructionsRootElement() {
        return cy.get('#instructions');
    }

    typeExampleSubmission(example: string) {
        cy.get('#example-file-submission-input').type(example, { parseSpecialCharSequences: false });
    }

    clickCreateNewExampleSubmission() {
        cy.intercept(POST, BASE_API + 'exercises/*/file-upload-submissions').as('createExampleSubmission');
        cy.get('#create-example-submission').click();
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
