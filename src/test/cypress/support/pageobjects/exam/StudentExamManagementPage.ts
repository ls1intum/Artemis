import { POST } from '../../constants';
import { COURSE_BASE } from './../../requests/CourseManagementRequests';
/**
 * A class which encapsulates UI selectors and actions for the student exam management page.
 */
export class StudentExamManagementPage {
    clickGenerateStudentExams() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/generate-student-exams').as('generateStudentExams');
        cy.get('#generateStudentExamsButton').click();
        return cy.wait('@generateStudentExams');
    }

    clickEvaluateQuizzes() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/student-exams/evaluate-quiz-exercises').as('evaluateQuizzes');
        cy.get('#evaluateQuizExercisesButton').click();
        return cy.wait('@evaluateQuizzes');
    }

    clickRegisterCourseStudents() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/register-course-students').as('registerCourseStudents');
        cy.get('[jhitranslate="artemisApp.examManagement.examStudents.registerAllFromCourse"]').click();
        return cy.wait('@registerCourseStudents');
    }
}
