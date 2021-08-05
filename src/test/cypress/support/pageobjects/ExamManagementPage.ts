/**
 * A class which encapsulates UI selectors and actions for the exam management page.
 */
export class ExamManagementPage {
    /**
     * Searches for an exam with the provided title.
     * @param examTitle the title of the exam.
     * @returns the row element of the found exam
     */
    getExamRow(examTitle: string) {
        return this.getExamSelector(examTitle).parents('tr');
    }

    /**
     * Deletes the exam with the specified title.
     * @param examTitle the exam title
     */
    deleteExam(examTitle: string) {
        this.getExamRow(examTitle).find('[deleteconfirmationtext="artemisApp.examManagement.delete.typeNameToConfirm"]').click();
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
        return cy.get('jhi-exam-management').contains(examTitle);
    }
}
