/**
 * A class which encapsulates UI selectors and actions for the exam management page.
 */
export class ExamManagementPage {
    /**
     * Searches for an exam with the provided title and returns a pageobject for further interactions.
     * @param examTitle the title of the exam.
     * @returns the pageobject which represents this exam row
     */
    getExamRow(examTitle: string) {
        return new ExamRow(this.getExamRowRoot(examTitle));
    }

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
     * @param examTitle the exam title
     */
    deleteExam(examTitle: string) {
        this.getExamRowRoot(examTitle).find('[deleteconfirmationtext="artemisApp.examManagement.delete.typeNameToConfirm"]').click();
        cy.get('.modal-footer').find('.btn-danger').should('be.disabled');
        cy.get('.modal-body').find('input').type(examTitle);
        cy.get('.modal-footer').find('.btn-danger').should('not.be.disabled').click();
    }

    /**
     * Clicks the create new exam button.
     */
    createNewExam() {
        cy.get('.create-exam').click();
    }

    /**
     * Returns the title element of the exam row.
     * @param examTitle the title to search for
     * @returns the element
     */
    getExamSelector(examTitle: string) {
        return cy.get('#exams-table').contains(examTitle);
    }
}

/**
 * Pageobject for a table row in the exams table.
 */
export class ExamRow {
    readonly root;

    /**
     * @param root the root <tr> element of the exam
     */
    constructor(root: Cypress.Chainable<JQuery<HTMLTableRowElement>>) {
        this.root = root;
    }

    /**
     * Opens the exercise groups page.
     */
    openExerciseGroups(examId: string) {
        this.root.find(`#exercises-button-${examId}-groups`).click();
    }

    /**
     * Opens the student registration page.
     */
    openStudentRegistration(examId: string) {
        this.root.find(`#student-button-${examId}`).click();
    }

    /**
     * Opens the student exams page.
     */
    openStudenExams(examId: string) {
        this.root.find(`#student-exams-${examId}`).click();
    }
}
