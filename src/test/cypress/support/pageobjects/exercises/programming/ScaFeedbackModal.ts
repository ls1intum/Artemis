/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    shouldShowPointChart() {
        cy.get('#feedback-chart').should('be.visible');
    }

    shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        // This is a workaround to avoid Cypress only returning the first element matching the id
        cy.get('.feedback-list')
            .contains(feedbackText)
            .scrollIntoView()
            .should('be.visible')
            .parents('.feedback-item')
            .find('.feedback-item__credits')
            .scrollIntoView()
            .should('contain.text', `-${pointReduction}P`)
            .and('be.visible');
    }

    closeModal() {
        cy.get('.feedback-header__close').click();
        cy.get('.result-detail-container').should('not.exist');
    }
}
