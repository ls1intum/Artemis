/**
 * A class which encapsulates UI selectors and actions for the static code analysis feedback modal in the online editor.
 */
export class ScaFeedbackModal {
    private readonly feedbackSelector = '#feedback-message';

    shouldShowPointChart() {
        cy.get('#feedback-chart').should('be.visible');
    }

    shouldShowFeedback(numberOfPassedTests: number, points: string) {
        cy.get(this.feedbackSelector).contains(`${numberOfPassedTests} passed tests`).should('be.visible');
        cy.get(this.feedbackSelector).contains(`${points}P`).should('be.visible');
    }

    shouldShowCodeIssue(feedbackText: string, pointReduction: string) {
        // This is a workaround to avoid Cypress only returning the first element matching the id
        cy.get('[id="feedback-text"]')
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
        cy.get('#feedback-close').click();
        cy.get('#result-detail-body').should('not.exist');
    }
}
