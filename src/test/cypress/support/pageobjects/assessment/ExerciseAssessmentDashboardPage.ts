/**
 * A class which encapsulates UI selectors and actions for the exercise assessment dashboard page.
 */
export class ExerciseAssessmentDashboardPage {
    readonly START_ASSESSING_SELECTOR = '#start-new-assessment';

    clickHaveReadInstructionsButton() {
        cy.get('#participate-in-assessment').click();
    }

    clickStartNewAssessment() {
        cy.reloadUntilFound(this.START_ASSESSING_SELECTOR);
        cy.get(this.START_ASSESSING_SELECTOR).click();
    }

    clickOpenAssessment() {
        cy.get('#open-assessment').click();
    }

    clickEvaluateComplaint() {
        cy.get('#evaluate-complaint').click();
    }

    getComplaintText() {
        return cy.get('#complaintTextArea');
    }

    getLockedMessage() {
        return cy.get('#assessmentLockedCurrentUser');
    }
}
