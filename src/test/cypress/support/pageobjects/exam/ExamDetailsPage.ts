class ExamDetailsPage {
    /**
     * Clicks the delete button and fills out the resulting dialog.
     * @param examTitle the exam title
     */
    deleteExam(examTitle: string) {
        cy.get('#delete-exam').click();
        cy.get('#delete').should('be.disabled');
        cy.get('#confirm-exercise-name').type(examTitle);
        cy.get('#delete').should('not.be.disabled').click();
    }
}
