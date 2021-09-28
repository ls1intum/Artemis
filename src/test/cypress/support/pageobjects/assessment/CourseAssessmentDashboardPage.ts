/**
 * A class which encapsulates UI selectors and actions for the course assessment dashboard page.
 */
export class CourseAssessmentDashboardPage {
    openComplaints(courseId: number) {
        cy.get(`[href="/course-management/${courseId}/complaints"]`).click();
    }

    clickExerciseDashboardButton() {
        cy.get('[jhitranslate="entity.action.exerciseDashboard"]').click();
    }

    checkShowFinishedExercises() {
        cy.get('#field_showFinishedExercise').check();
    }
}
