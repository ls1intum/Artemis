import { EXERCISE_BASE, POST, PUT } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the file upload editor page.
 */
export class FileUploadEditorPage {
    attachFile(filePath: string) {
        cy.get('#fileUploadInput').attachFile(filePath);
    }

    attachFileExam(filePath: string) {
        cy.get('#fileUploadInput').attachFile(filePath);
        cy.get('#file-upload-submit').click();
    }

    /**
     * Saves the file upload submission and continues to the next exercise in the exam. This button is only available in exam mode!
     */
    saveAndContinue() {
        cy.intercept(PUT, `${EXERCISE_BASE}/*/file-upload-submissions`).as('savedSubmission');
        cy.get('#save').click();
        return cy.wait('@savedSubmission');
    }

    submit() {
        cy.intercept(POST, `${EXERCISE_BASE}/*/file-upload-submissions`).as('fileUploadSubmission');
        cy.get('#submit').click();
        return cy.wait('@fileUploadSubmission');
    }

    shouldShowExerciseTitleInHeader(exerciseTitle: string) {
        cy.get('#participation-header').contains(exerciseTitle).should('be.visible');
    }

    shouldShowProblemStatement() {
        cy.get('#problem-statement').should('be.visible');
    }
}
