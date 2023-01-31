import { BASE_API, PUT } from '../../../constants';
import { getExercise } from '../../../utils';

/**
 * A class which encapsulates UI selectors and actions for the text editor page.
 */
export class TextEditorPage {
    typeSubmission(exerciseID: number, submission: string) {
        getExercise(exerciseID).find('#text-editor').type(submission, { parseSpecialCharSequences: false });
    }

    /**
     * Saves the text submission and continues to the next exercise in the exam. This button is only available in exam mode!
     */
    saveAndContinue() {
        cy.intercept(PUT, BASE_API + 'exercises/*/text-submissions').as('savedSubmission');
        cy.get('#save').click();
        return cy.wait('@savedSubmission');
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'exercises/*/text-submissions').as('textSubmission');
        cy.get('#submit').click();
        return cy.wait('@textSubmission');
    }

    shouldShowExerciseTitleInHeader(exerciseTitle: string) {
        cy.get('#participation-header').contains(exerciseTitle).should('be.visible');
    }

    shouldShowProblemStatement() {
        cy.get('#problem-statement').should('be.visible');
    }

    shouldShowNumberOfWords(numberOfWords: number) {
        cy.get('#word-count').should('contain.text', numberOfWords).and('be.visible');
    }

    shouldShowNumberOfCharacters(numberOfWords: number) {
        cy.get('#character-count').should('contain.text', numberOfWords).and('be.visible');
    }
}
