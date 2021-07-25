/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Example Submission Page.
 * Path: /course-management/${courseID}/modeling-exercises/${exerciseID}/example-submissions
 */
export class ModelingExerciseExampleSubmissionPage {

    /**
     * Locates and clicks the Create Example Submission Button
     * */
    createExampleSubmission() {
        cy.contains('Create Example Submission').click();
    }

    /**
     * Locates and clicks the Create New Example Submission Button
     * */
    createNewExampleSubmission() {
        cy.contains('Create new Example Submission').click();
    }

    switchToAssessmentView() {
        cy.contains('Show Assessment').click();
    }

    openAssessmentForComponent(componentNumber: number) {
        cy.getSettled(`.sc-furvIG >> :nth-child(${componentNumber})`).dblclick('top', {force: true});
    }

    assessComponent(points: number, feedback: string) {
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type(`${points}`);
        cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3)').type(`${feedback}`);
    }

    saveExampleAssessment() {
        cy.contains('Save Example Assessment').click();
    }
}
