/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    private readonly feedbackSelector = '#feedback-message';

    shouldShowPointChart() {
        cy.get('#chart').should('be.visible');
    }

    shouldShowFeedback(numberOfPassedTests: number, points: string) {
        cy.get(this.feedbackSelector).contains(`${numberOfPassedTests} passed tests`).should('be.visible');
        cy.get(this.feedbackSelector).contains(`${points}P`).should('be.visible');
    }

    shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        // We have to query for a css class here. If we query for an id Cypress will return the first id it finds instead of all elements matching the selector.
        cy.get('.feedback-text')
            .contains(feedbackText)
            .scrollIntoView()
            .should('be.visible')
            .parents(this.feedbackSelector)
            .find('#feedback-points')
            .scrollIntoView()
            .should('contain.text', `-${pointReduction}P`)
            .and('be.visible');
    }

    closeModal() {
        cy.get('#feedback-close').find('.btn').click();
        cy.get('#result-detail-body').should('not.exist');
    }
}
