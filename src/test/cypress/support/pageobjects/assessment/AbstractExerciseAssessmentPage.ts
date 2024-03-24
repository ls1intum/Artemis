import { BASE_API, ExerciseType, PUT } from '../../constants';

/**
 * Parent class for all exercise assessment pages.
 */
export abstract class AbstractExerciseAssessmentPage {
    readonly unreferencedFeedbackSelector = '.unreferenced-feedback-detail';

    addNewFeedback(points: number, feedback?: string) {
        cy.get('.add-unreferenced-feedback').click();
        cy.get(this.unreferencedFeedbackSelector).find('#feedback-points').clear().type(points.toString());
        if (feedback) {
            cy.get(this.unreferencedFeedbackSelector).find('#feedback-textarea').clear().type(feedback);
        }
    }

    submitWithoutInterception() {
        cy.get('#submit').click();
    }

    submit() {
        cy.intercept(PUT, `${BASE_API}/participations/*/manual-results?submit=true`).as('submitAssessment');
        this.submitWithoutInterception();
        return cy.wait('@submitAssessment');
    }

    rejectComplaint(response: string, examMode: boolean, exerciseType: ExerciseType) {
        return this.handleComplaint(response, false, exerciseType, examMode);
    }

    acceptComplaint(response: string, examMode: boolean, exerciseType: ExerciseType) {
        return this.handleComplaint(response, true, exerciseType, examMode);
    }

    private handleComplaint(response: string, accept: boolean, exerciseType: ExerciseType, examMode: boolean) {
        if (exerciseType !== ExerciseType.MODELING && !examMode) {
            cy.get('#show-complaint').click();
        }
        cy.get('#responseTextArea').type(response, { parseSpecialCharSequences: false });
        switch (exerciseType) {
            case ExerciseType.PROGRAMMING:
                cy.intercept(PUT, `${BASE_API}/programming-submissions/*/assessment-after-complaint`).as('complaintAnswer');
                break;
            case ExerciseType.TEXT:
                cy.intercept(PUT, `${BASE_API}/participations/*/submissions/*/text-assessment-after-complaint`).as('complaintAnswer');
                break;
            case ExerciseType.MODELING:
                cy.intercept(PUT, `${BASE_API}/complaint-responses/complaint/*/resolve`).as('complaintAnswer');
                break;
            case ExerciseType.FILE_UPLOAD:
                cy.intercept(PUT, `${BASE_API}/file-upload-submissions/*/assessment-after-complaint`).as('complaintAnswer');
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
