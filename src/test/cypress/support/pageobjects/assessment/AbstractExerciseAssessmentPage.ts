import { PUT, BASE_API } from './../../constants';
/**
 * Parent class for all exercise assessment pages.
 */
export abstract class AbstractExerciseAssessmentPage {
    readonly unreferencedFeedbackSelector = 'jhi-unreferenced-feedback';

    getInstructionsRootElement() {
        return cy.get('[jhitranslate="artemisApp.textAssessment.instructions"]').parents('.card');
    }

    addNewFeedback(points: number, feedback?: string) {
        cy.get('.btn-success').contains('Add new Feedback').click();
        cy.get(this.unreferencedFeedbackSelector).find('input[type="number"]').clear().type(points.toString());
        if (feedback) {
            cy.get(this.unreferencedFeedbackSelector).find('textarea').clear().type(feedback);
        }
    }

    submit() {
        cy.get('[jhitranslate="entity.action.submit"]').click();
        cy.contains('Your assessment was submitted successfully!').should('be.visible');
    }

    rejectComplaint(response: string) {
        return this.handleComplaint(response, false);
    }

    acceptComplaint(response: string) {
        return this.handleComplaint(response, true);
    }

    private handleComplaint(response: string, accept: boolean) {
        cy.get('tr > .text-center >').click();
        cy.get('#responseTextArea').type(response, { parseSpecialCharSequences: false });
        cy.intercept(PUT, BASE_API + 'participations/*/submissions/*/text-assessment-after-complaint').as('complaintAnswer');
        if (accept) {
            cy.get('#acceptComplaintButton').click();
        } else {
            cy.get('#rejectComplaintButton').click();
        }
        return cy.wait('@complaintAnswer');
    }
}
