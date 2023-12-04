/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamDetailsPage {
    /**
     * Deletes this exam.
     * @param examTitle the exam title to confirm the deletion
     */
    deleteExam(examTitle: string) {
        cy.get('#exam-delete').click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-entity-name').type(examTitle);
        cy.get('#delete').should('not.be.disabled').click();
    }
}
