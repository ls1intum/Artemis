import { PUT, BASE_API } from './../../../constants';
/**
 * A class which encapsulates UI selectors and actions for the text editor page.
 */
export class TextEditorPage {
    typeSubmission(submission: string) {
        cy.get('textarea').type(submission, { parseSpecialCharSequences: false });
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/text-submissions').as('textSubmission');
        cy.get('.btn-primary').click();
        return cy.wait('@textSubmission');
    }
}
