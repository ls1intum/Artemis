import { COURSE_BASE, POST } from '../../constants';

/**
 * A class which encapsulates UI selectors and actions for the student exam management page.
 */
export class StudentExamManagementPage {
    clickGenerateStudentExams() {
        cy.intercept(POST, `${COURSE_BASE}/*/exams/*/generate-student-exams`).as('generateStudentExams');
        this.getGenerateStudentExamsButton().click();
        return cy.wait('@generateStudentExams');
    }

    clickRegisterCourseStudents() {
        cy.intercept(POST, `${COURSE_BASE}/*/exams/*/register-course-students`).as('registerCourseStudents');
        cy.get('#register-course-students').click();
        return cy.wait('@registerCourseStudents');
    }

    getGenerateStudentExamsButton() {
        return cy.get('#generateStudentExamsButton');
    }

    getRegisteredStudents() {
        return cy.get('#registered-students');
    }

    checkExamStudent(username: string) {
        return cy.get('#student-exam').find(`[href$="user-management/${username}"]`).should('have.length', 1);
    }

    getStudentExamRows() {
        return cy.get('#student-exam').find('.datatable-body-row');
    }

    getStudentExamRow(username: string) {
        return cy.get('#student-exam').find(`a[href$="user-management/${username}"]`).closest('.datatable-body-row');
    }

    getExamProperty(username: string, property: string) {
        cy.get('.datatable-header-cell').each(($headerCell, index) => {
            if ($headerCell.text().includes(property)) {
                this.getStudentExamRow(username).within(() => {
                    cy.get('.datatable-body-cell-label').eq(index).as('bodyCellLabel');
                });
            }
        });
        return cy.get('@bodyCellLabel');
    }

    checkExamProperty(username: string, property: string, value: string) {
        this.getExamProperty(username, property).contains(value);
    }

    typeSearchText(text: string) {
        cy.get('#typeahead-basic').focus().clear().type(text);
    }
}
