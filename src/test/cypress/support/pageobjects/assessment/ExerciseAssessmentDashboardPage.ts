/**
 * A class which encapsulates UI selectors and actions for the exercise assessment dashboard page.
 */
export class ExerciseAssessmentDashboardPage {
    readonly startAssessingSelector = '#start-new-assessment';

    clickHaveReadInstructionsButton() {
        cy.get('#participate-in-assessment').click();
    }

    clickStartNewAssessment() {
        cy.reloadUntilFound(this.startAssessingSelector);
        cy.get(this.startAssessingSelector).click();
    }
}
