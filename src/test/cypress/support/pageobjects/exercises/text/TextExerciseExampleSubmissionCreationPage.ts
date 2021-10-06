import { BASE_API } from '../../../constants';
import { POST } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the text exercise example submission creation page.
 */
export class TextExerciseExampleSubmissionCreationPage {
    typeExampleSubmission(example: string) {
        cy.get('textarea').type(example, { parseSpecialCharSequences: false });
    }

    clickCreateNewExampleSubmission() {
        cy.intercept(POST, BASE_API + 'exercises/*/example-submissions').as('createExampleSubmission');
        cy.get('[data-icon="save"]').click();
        return cy.wait('@createExampleSubmission');
    }
}
