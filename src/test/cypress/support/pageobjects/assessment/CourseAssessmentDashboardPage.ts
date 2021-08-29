/**
 * A class which encapsulates UI selectors and actions for the course assessment dashboard page.
 */
export class CourseAssessmentDashboardPage {
    clickExerciseDashboardButton() {
        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').click();
    }

    checkShowFinishedExercises() {
        cy.get('#field_showFinishedExercise').check();
    }
}
