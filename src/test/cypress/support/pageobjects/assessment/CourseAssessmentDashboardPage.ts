import { COURSE_BASE } from '../../requests/CourseManagementRequests';
import { POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course assessment dashboard page.
 */
export class CourseAssessmentDashboardPage {
    readonly exerciseDashboardButtonSelector = '#open-exercise-dashboard';

    openComplaints() {
        cy.get('#open-complaints').click();
    }

    showTheComplaint() {
        cy.get('#show-complaint').click();
    }

    clickExerciseDashboardButton() {
        // Sometimes the page does not load properly, so we reload it if the button is not found
        cy.reloadUntilFound(this.exerciseDashboardButtonSelector);
        cy.get(this.exerciseDashboardButtonSelector).click();
    }

    clickEvaluateQuizzes() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/student-exams/evaluate-quiz-exercises').as('evaluateQuizzes');
        cy.get('#evaluateQuizExercisesButton').click();
        return cy.wait('@evaluateQuizzes');
    }
}
