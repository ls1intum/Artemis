import { PUT, BASE_API } from '../../constants';
import { CypressExerciseType } from '../../requests/CourseManagementRequests';
/**
 * Parent class for all exercise assessment pages.
 */
export abstract class AbstractExerciseAssessmentPage {
    readonly unreferencedFeedbackSelector = 'jhi-unreferenced-feedback';

    getInstructionsRootElement() {
        return cy.get('[jhitranslate="artemisApp.exercise.instructions"]').parents('.card');
    }

    addNewFeedback(points: number, feedback?: string) {
        cy.get('.btn-success').contains('Add new Feedback').click();
        cy.get(this.unreferencedFeedbackSelector).find('input[type="number"]').clear().type(points.toString());
        if (feedback) {
            cy.get(this.unreferencedFeedbackSelector).find('textarea').clear().type(feedback);
        }
    }

    submitWithoutInterception() {
        cy.get('[jhitranslate="entity.action.submit"]').click();
    }

    submit() {
        cy.intercept(PUT, BASE_API + 'participations/*/manual-results?submit=true').as('submitAssessment');
        this.submitWithoutInterception();
        return cy.wait('@submitAssessment');
        // TODO: The alert is currently broken
        // cy.contains('Your assessment was submitted successfully!').should('be.visible');
    }

    rejectComplaint(response: string, exerciseType: CypressExerciseType) {
        return this.handleComplaint(response, false, exerciseType);
    }

    acceptComplaint(response: string, exerciseType: CypressExerciseType) {
        return this.handleComplaint(response, true, exerciseType);
    }

    private handleComplaint(response: string, accept: boolean, exerciseType: CypressExerciseType) {
        cy.get('tr > .text-center >').click();
        cy.get('#responseTextArea').type(response, { parseSpecialCharSequences: false });
        switch (exerciseType) {
            case CypressExerciseType.PROGRAMMING:
                cy.intercept(PUT, BASE_API + 'programming-submissions/*/assessment-after-complaint').as('complaintAnswer');
                break;
            case CypressExerciseType.TEXT:
                cy.intercept(PUT, BASE_API + 'participations/*/submissions/*/text-assessment-after-complaint').as('complaintAnswer');
                break;
            default:
                throw new Error(`Exercise type '${exerciseType}' is not supported yet!`);
        }
        if (accept) {
            cy.get('#acceptComplaintButton').click();
        } else {
            cy.get('#rejectComplaintButton').click();
        }
        return cy.wait('@complaintAnswer');
    }
}
