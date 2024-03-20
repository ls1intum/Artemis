import { EXERCISE_BASE, PUT } from '../../../constants';
import { getExercise } from '../../../utils';

/**
 * A class which encapsulates UI selectors and actions for the text editor page.
 */
export class TextEditorPage {
    typeSubmission(exerciseID: number, submission: string) {
        getExercise(exerciseID).find('#text-editor').type(submission, { parseSpecialCharSequences: false });
    }

    clearSubmission(exerciseID: number) {
        getExercise(exerciseID).find('#text-editor').clear();
    }

    checkCurrentContent(exerciseID: number, expectedContent: string) {
        cy.fixture(expectedContent).then((text) => {
            getExercise(exerciseID).find('#text-editor').should('have.value', text);
        });
    }

    /**
     * Saves the text submission and continues to the next exercise in the exam. This button is only available in exam mode!
     */
    saveAndContinue() {
        cy.intercept(PUT, `${EXERCISE_BASE}/*/text-submissions`).as('savedSubmission');
        cy.get('#save').click();
        return cy.wait('@savedSubmission');
    }

    submit() {
        cy.intercept(PUT, `${EXERCISE_BASE}/*/text-submissions`).as('textSubmission');
        cy.get('#submit button').click();
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
