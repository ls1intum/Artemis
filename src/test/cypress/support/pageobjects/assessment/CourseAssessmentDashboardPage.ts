import { COURSE_BASE, POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the course assessment dashboard page.
 */
export class CourseAssessmentDashboardPage {
    openComplaints() {
        cy.get('#open-complaints').click();
    }

    showTheComplaint() {
        cy.get('#show-complaint').click();
    }

    clickExerciseDashboardButton() {
        // Sometimes the page does not load properly, so we reload it if the button is not found
        cy.reloadUntilFound('#open-exercise-dashboard');
        cy.get('#open-exercise-dashboard').click();
    }

    clickEvaluateQuizzes() {
        cy.intercept(POST, `${COURSE_BASE}/*/exams/*/student-exams/evaluate-quiz-exercises`).as('evaluateQuizzes');
        cy.get('#evaluateQuizExercisesButton').click();
        return cy.wait('@evaluateQuizzes');
    }
}
