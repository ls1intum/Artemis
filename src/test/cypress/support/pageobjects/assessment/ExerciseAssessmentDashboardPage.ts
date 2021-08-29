/**
 * A class which encapsulates UI selectors and actions for the exercise assessment dashboard page.
 */
export class ExerciseAssessmentDashboardPage {
    clickHaveReadInstructionsButton() {
        cy.get('.guided-tour-instructions-button').click();
    }

    clickStartNewAssessment() {
        cy.get('.guided-tour-new-assessment-btn').click();
    }
}
