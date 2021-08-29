/**
 * A class which encapsulates UI selectors and actions for the exercise assessment dashboard page.
 */
export class ExerciseAssessmentDashboardPage {
    clickExerciseDashboardButton() {
        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').click();
    }
}
