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
        cy.get('jhi-header-participation-page').contains(exerciseTitle).should('be.visible');
    }

    shouldShowProblemStatement() {
        cy.get('[jhitranslate="artemisApp.exercise.problemStatement"]').should('be.visible');
    }

    getHeaderElement() {
        return cy.get('jhi-header-participation-page');
    }

    shouldShowNumberOfWords(numberOfWords: number) {
        cy.get('.badge').contains(`Number of words: ${numberOfWords}`).should('be.visible');
    }

    shouldShowNumberOfCharacters(numberOfWords: number) {
        cy.get('.badge').contains(`Number of characters: ${numberOfWords}`).should('be.visible');
    }

    shouldShowAlert() {
        cy.get('.alert-success').should('be.visible');
    }

    shouldShowNoGradedResultAvailable() {
        cy.get('[jhitranslate="artemisApp.result.noResult"]').should('be.visible');
    }
}
