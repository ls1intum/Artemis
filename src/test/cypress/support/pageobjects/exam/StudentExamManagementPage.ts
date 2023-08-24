import { COURSE_BASE, POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the student exam management page.
 */
export class StudentExamManagementPage {
    clickGenerateStudentExams() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/generate-student-exams').as('generateStudentExams');
        this.getGenerateStudentExamsButton().click();
        return cy.wait('@generateStudentExams');
    }

    clickRegisterCourseStudents() {
        cy.intercept(POST, COURSE_BASE + '*/exams/*/register-course-students').as('registerCourseStudents');
        cy.get('#register-course-students').click();
        return cy.wait('@registerCourseStudents');
    }

    getGenerateStudentExamsButton() {
        return cy.get('#generateStudentExamsButton');
    }

    getRegisteredStudents() {
        return cy.get('#registered-students');
    }
}
