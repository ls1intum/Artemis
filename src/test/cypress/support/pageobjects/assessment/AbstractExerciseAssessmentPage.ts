/**
 * Parent class for all exercise assessment pages.
 */
export class AbstractExerciseAssessmentPage {
    getInstructionsRootElement() {
        return cy.get('[jhitranslate="artemisApp.textAssessment.instructions"]').parent('.card');
    }

    addNewFeedback(points: number, feedback?: string) {
        cy.get('.btn-sucess').contains('Add new Feedback').click();
        cy.get('.col-lg-6 >>>> :nth-child(1) > :nth-child(2)').clear().type(points.toString());
        if (feedback) {
            cy.get('.col-lg-6 >>>> :nth-child(2) > :nth-child(2)').type(feedback);
        }
    }
}
