/**
 * A class which encapsulates UI selectors and actions for the exam management page.
 */
export class ExamManagementPage {
    /**
     * Searches for an exam with the provided title.
     * @param examTitle the title of the exam.
     * @returns the row element of the found exam
     */
    getExamRowRoot(examTitle: string) {
        return this.getExamSelector(examTitle).parents('tr');
    }

    /**
     * Deletes the exam with the specified title.
     * @param examId the exam ID
     * @param examTitle the exam ID
     */
    deleteExam(examId: string, examTitle: string) {
        cy.get('#delete-exam-' + examId).click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-exercise-name').type(examTitle);
        cy.get('#delete').should('not.be.disabled').click();
    }

    /**
     * Clicks the create new exam button.
     */
    createNewExam() {
        cy.get('#create-exam').click();
    }

    /**
     * Returns the title element of the exam row.
     * @param examTitle the title to search for
     * @returns the element
     */
    getExamSelector(examTitle: string) {
        return cy.get('#exams-table').contains(examTitle);
    }

    /**
     * Opens the exercise groups page.
     */
    openExerciseGroups(examId: number) {
        cy.get(`#exercises-button-${examId}-groups`).click();
    }

    /**
     * Opens the student registration page.
     */
    openStudentRegistration(examId: number) {
        cy.get(`#student-button-${examId}`).click();
    }

    /**
     * Opens the student exams page.
     */
    openStudenExams(examId: number) {
        cy.get(`#student-exams-${examId}`).click();
    }

    /**
     * Opens the exam assessment dashboard
     * @param examId the id of the exam
     * @param timeout how long to wait for the assessment dashboard button
     */
    openAssessmentDashboard(examId: number, timeout: number) {
        cy.get('#exercises-button-' + examId, { timeout }).click();
    }
}
