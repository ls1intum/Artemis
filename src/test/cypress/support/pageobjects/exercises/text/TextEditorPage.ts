import { BASE_API, PUT } from '../../../constants';
/**
 * A class which encapsulates UI selectors and actions for the text editor page.
 */
export class TextEditorPage {
    typeSubmission(submission: string) {
        cy.get('textarea').type(submission, { parseSpecialCharSequences: false });
    }

    /**
     * Saves the text submission and continues to the next exercise in the exam. This button is only available in exam mode!
     */
    saveAndContinue() {
        cy.intercept(PUT, BASE_API + 'exercises/*/text-submissions').as('savedSubmission');
        cy.get('jhi-exam-navigation-bar').find('.btn-primary').click();
        return cy.wait('@savedSubmission');
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/text-submissions').as('textSubmission');
        cy.get('.btn-primary').click();
        return cy.wait('@textSubmission');
    }

    shouldShowExerciseTitleInHeader(exerciseTitle: string) {
        cy.get('#participation-header').contains(exerciseTitle).should('be.visible');
    }

    shouldShowProblemStatement() {
        cy.get('#problem-statement').should('be.visible');
    }

    getHeaderElement() {
        return cy.get('jhi-header-participation-page');
    }

    shouldShowNumberOfWords(numberOfWords: number) {
        cy.get('#word-count').contains(numberOfWords).should('be.visible');
    }

    shouldShowNumberOfCharacters(numberOfWords: number) {
        cy.get('#character-count').contains(numberOfWords).should('be.visible');
    }
}
