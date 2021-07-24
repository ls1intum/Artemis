/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Example Submission Page.
 * Path: /course-management/${courseID}/modeling-exercises/${exerciseID}/example-submissions
 */
export class ModelingExerciseExampleSubmissionPage {
    MODELING_SPACE = '.sc-furvIG';
    COMPONENT_CONTAINER = '.sc-ksdxAp';

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

    /**
     * Adds a Modeling Component to the Model
     * */
    addComponentToExampleSubmission(componentNumber: number) {
        cy.get(`${this.COMPONENT_CONTAINER} > :nth-child(${componentNumber}) > :nth-child(1) > :nth-child(1)`)
            .drag(`${this.MODELING_SPACE}`, { position: 'bottomLeft', force: true });
    }

    switchToAssessmentView() {
        cy.contains('Show Assessment').click();
    }

    openAssessmentForComponent(componentNumber: number) {
        cy.getSettled(`.sc-furvIG >> :nth-child(${componentNumber})`).dblclick('top');
    }
}
