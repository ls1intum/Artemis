/**
 * A class which encapsulates UI selectors and actions for the course assessment dashboard page.
 */
export class CourseAssessmentDashboardPage {
    readonly exerciseDashboardButtonSelector = '[jhitranslate="entity.action.exerciseDashboard"]';

    openComplaints(courseId: number) {
        cy.get(`[href="/course-management/${courseId}/complaints"]`).click();
    }

    clickExerciseDashboardButton() {
        // Sometimes the page does not load properly, so we reload it if the button is not found
        cy.waitUntil(
            () => {
                const found = Cypress.$(this.exerciseDashboardButtonSelector).length > 0;
                if (!found) {
                    cy.reload();
                }
                return found;
            },
            {
                interval: 2000,
                timeout: 20000,
                errorMsg: 'Timed out finding the exercise dashboard button',
            },
        );
        cy.get(this.exerciseDashboardButtonSelector).click();
    }

    checkShowFinishedExercises() {
        cy.get('#field_showFinishedExercise').check();
    }
}
